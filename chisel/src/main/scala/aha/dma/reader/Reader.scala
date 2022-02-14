package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Decoupled, is, switch}

/* Project Imports */
import aha.dma.{CmdBundle, log2}
import aha.dma.util.{AXI4RdIntfView, AXI4AddrPayload}

/**
 * AXI4 Read Engine
 *
 * @constructor         constructs a AXI4 read engine with an AXI ID width,
 *                      address bus width, data bus width, and the type of a
 *                      transfer command bundle
 * @tparam R            transfer command bundle type for reader
 * @param IdWidth       the width of AXI ID signals (AWID, BID, ARID, and RID)
 * @param AddrWidth     the width of AXI address busses (AWADDR and ARADDR)
 * @param DataWidth     the width of AXI data busses (WDATA and RDATA)
 * @param Cmd           the bundle to use for transfer commands
 *
 * @note transfer addresses must be aligned to the data bus width (DataWidth)
 * @note transfers must not cross the [[MAX_BURST_BYTES]]
 */
class Reader[R <: CmdBundle](IdWidth: Int,
                             AddrWidth: Int,
                             DataWidth: Int,
                             Cmd : R) extends RawModule {

    import Reader.State
    import Reader.State._

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK        = IO(Input(Clock()))
    val ARESETn     = IO(Input(Bool()))

    // Command/Stat Interfaces
    val CmdIntf     = IO(Flipped(Decoupled(Cmd)))
    val StatIntf    = IO(Decoupled(UInt(2.W)))

    // Data Packet Interface
    val DataIntf    = IO(Decoupled(UInt(DataWidth.W)))

    // AXI4 Read Interface
    val ReadIntf    = IO(new AXI4RdIntfView(IdWidth, AddrWidth, DataWidth))

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset       = (!ARESETn).asAsyncReset

    // =========================================================================
    // Internal Logic
    // =========================================================================

    val state       = withClockAndReset(ACLK, reset) { RegInit(sIDLE) }
    val last_beat   = Wire(Bool())
    val read_resp   = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }

    // AR Channel Signals
    val ar_size     = log2(DataWidth/8)
    val ar_addr     = (CmdIntf.bits.Address >> ar_size.U) << ar_size.U
    val ar_len      = Wire(UInt(8.W))
    val ar_valid    = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val ar_payload  = withClockAndReset(ACLK, reset) {
                        RegInit(0.U.asTypeOf(new AXI4AddrPayload(IdWidth, AddrWidth)))
                      }

    // R Channel Signal
    val r_ready     = Wire(Bool())

    withClockAndReset(ACLK, reset) {
        //
        // State Register Update Logic
        //
        switch (state) {
            is (sIDLE) {
                when (CmdIntf.valid) {
                    state   := sADDR
                }
            } // sIDLE
            is (sADDR) {
                when (ReadIntf.AR.ready) {
                    state   := sDATA
                }
            } // sADDR
            is (sDATA) {
                when (last_beat) {
                    state   := sSTAT
                }
            } // sDATA
            is (sSTAT) {
                when (StatIntf.ready) {
                    state   := sIDLE
                }
            } // sSTAT
        } // switch(state)

        //
        // AR Channel Signals
        //
        switch (state) {
            is (sIDLE) {
                when (CmdIntf.valid) {
                    ar_valid            := true.B
                    ar_payload.ID       := 0.U
                    ar_payload.ADDR     := ar_addr
                    ar_payload.LEN      := ar_len
                    ar_payload.SIZE     := ar_size.U
                    ar_payload.BURST    := 1.U
                    ar_payload.LOCK     := false.B
                    ar_payload.CACHE    := 2.U
                    ar_payload.PROT     := 2.U
                }.otherwise {
                    ar_valid            := false.B
                }
            } // sIDLE
            is (sADDR) {
                when (ReadIntf.AR.ready) {
                    ar_valid            := false.B
                }
            } // sADDR
        } // switch(state)

        ReadIntf.AR.valid   := ar_valid
        ReadIntf.AR.bits    := ar_payload

        //
        // Internal Read Response Accumulation
        // (read errors are sticky)
        //
        switch (state) {
            is (sIDLE) {
                read_resp   := 0.U
            } // sIDLE
            is (sDATA) {
                when (ReadIntf.R.valid && ReadIntf.R.ready) {
                    when (read_resp === 0.U) {
                        read_resp   := ReadIntf.R.bits.RESP
                    }
                }
            } // sDATA
        } // switch(state)

        //
        // RREADY
        //
        when (state === sDATA) {
            r_ready     := DataIntf.ready
        }.otherwise {
            r_ready     := false.B
        }

        ReadIntf.R.ready := r_ready

        //
        // Internal Signals
        //
        last_beat   := ReadIntf.R.valid && ReadIntf.R.ready && ReadIntf.R.bits.LAST
        ar_len      := ((CmdIntf.bits.NumBytes >> ar_size.U) +
                         CmdIntf.bits.NumBytes(ar_size-1, 0).orR) - 1.U

        //
        // Status Signals
        //
        StatIntf.valid  := state === sSTAT
        StatIntf.bits   := read_resp

        //
        // Data Interface Signals
        //
        DataIntf.valid  := (state === sDATA) && ReadIntf.R.valid
        DataIntf.bits   := ReadIntf.R.bits.DATA

        //
        // Command Interface Ready
        //
        CmdIntf.ready   := state === sIDLE

    } // withClockAndReset(ACLK, reset)

} // class Reader

/**
 * Provides the states for the [[Reader]] finite state machine
 */
object Reader {

    /**
     * States for Reader FSM
     */
    object State extends ChiselEnum {
        val sIDLE, sADDR, sDATA, sSTAT = Value
    } // object State
} // object Reader
