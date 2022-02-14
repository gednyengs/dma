package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Decoupled, Fill, is, switch}

/* Project Imports */
import aha.dma.{clog2, CmdBundle, log2, MAX_BURST_BYTES}
import aha.dma.util.{AXI4WrIntfView, AXI4AddrPayload}

/**
 * AXI4 Write Engine
 *
 * @constructor         constructs a AXI4 write engine with an AXI ID width,
 *                      address bus width, data bus width, and the type of a
 *                      transfer command bundle
 * @tparam W            transfer command bundle type for writer
 * @param IdWidth       the width of AXI ID signals (AWID, BID, ARID, and RID)
 * @param AddrWidth     the width of AXI address busses (AWADDR and ARADDR)
 * @param DataWidth     the width of AXI data busses (WDATA and RDATA)
 * @param Cmd           the bundle to use for transfer commands
 *
 * @note transfer addresses must be aligned to the data bus width (DataWidth)
 * @note transfers must not cross the [[MAX_BURST_BYTES]]
 */
class Writer[W <: CmdBundle](IdWidth: Int,
                             AddrWidth: Int,
                             DataWidth: Int,
                             Cmd : W) extends RawModule {

     import Writer.State
     import Writer.State._

     // =========================================================================
     // I/O
     // =========================================================================

     // Clock and Reset
     val ACLK       = IO(Input(Clock()))
     val ARESETn    = IO(Input(Bool()))

     // Command/Stat Interfaces
     val CmdIntf    = IO(Flipped(Decoupled(Cmd)))
     val StatIntf   = IO(Decoupled(UInt(2.W)))

     // Data Packet Interface
     val DataIntf   = IO(Flipped(Decoupled(UInt(DataWidth.W))))

     // AXI4 Write Interface
     val WriteIntf  = IO(new AXI4WrIntfView(IdWidth, AddrWidth, DataWidth))

     // =========================================================================
     // Chisel Work-Around for Active-Low Reset
     // =========================================================================

     val reset      = (!ARESETn).asAsyncReset

     // =========================================================================
     // Internal Logic
     // =========================================================================

     val n_lanes    = DataWidth/8

     val state      = withClockAndReset(ACLK, reset) { RegInit(sIDLE) }
     val write_resp = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }
     val num_bytes  = withClockAndReset(ACLK, reset) { RegInit(0.U(clog2(MAX_BURST_BYTES).W)) }
     val last_beat  = Wire(Bool())

     // AW Channel Signals
     val aw_size    = log2(DataWidth/8)
     val aw_addr    = (CmdIntf.bits.Address >> aw_size.U) << aw_size.U
     val aw_len     = Wire(UInt(8.W))
     val aw_valid   = withClockAndReset(ACLK, reset) { RegInit(false.B) }
     val aw_payload = withClockAndReset(ACLK, reset) {
                         RegInit(0.U.asTypeOf(new AXI4AddrPayload(IdWidth, AddrWidth)))
                       }

     // W Channel Signals
     val w_strb     = Wire(UInt((DataWidth/8).W))
     val w_valid    = Wire(Bool())
     val w_last     = Wire(Bool())

     withClockAndReset(ACLK, reset) {

         //
         // State Register Update Logic
         //
         switch (state) {
             is (sIDLE) {
                 when (CmdIntf.valid) {
                     state  := sADDR
                 }
             } // sIDLE
             is (sADDR) {
                when (WriteIntf.AW.ready) {
                    state   := sDATA
                }
             } // sADDR
             is (sDATA) {
                 when (last_beat) {
                     state  := sRESP
                 }
             } // sDATA
             is (sRESP) {
                 when (WriteIntf.B.valid) {
                     state  := sSTAT
                 }
             } // sRESP
             is (sSTAT) {
                 when (StatIntf.ready) {
                     state  := sIDLE
                 }
             }
         } // switch(state)

         //
         // AW Channel Signals
         //
         switch (state) {
             is (sIDLE) {
                 when (CmdIntf.valid) {
                     aw_valid            := true.B
                     aw_payload.ID       := 0.U
                     aw_payload.ADDR     := aw_addr
                     aw_payload.LEN      := aw_len
                     aw_payload.SIZE     := aw_size.U
                     aw_payload.BURST    := 1.U
                     aw_payload.LOCK     := false.B
                     aw_payload.CACHE    := 2.U
                     aw_payload.PROT     := 2.U
                 }.otherwise {
                     aw_valid            := false.B
                 }
             } // sIDLE
             is (sADDR) {
                 when (WriteIntf.AW.ready) {
                     aw_valid            := false.B
                 }
             } // sADDR
         } // switch(state)

         WriteIntf.AW.valid     := aw_valid
         WriteIntf.AW.bits      := aw_payload

         //
         // Write Response Capture
         //
         switch (state) {
             is (sIDLE) {
                 write_resp     := 0.U
             } // sIDLE
             is (sRESP) {
                 when (WriteIntf.B.valid) {
                     write_resp := WriteIntf.B.bits.RESP
                 }
             } // sRESP
         } // switch (state)

         //
         // WDATA
         //
         when (state === sDATA) {
             WriteIntf.W.bits.DATA  := DataIntf.bits
         }.otherwise {
             WriteIntf.W.bits.DATA  := 0.U
         }

         //
         // num_bytes
         //
         switch (state) {
             is (sIDLE) {
                 when (CmdIntf.valid) {
                     num_bytes  := CmdIntf.bits.NumBytes
                 }.otherwise {
                     num_bytes  := 0.U
                 }
             } // sIDLE
             is (sDATA) {
                when (WriteIntf.W.valid && WriteIntf.W.ready) {
                    when (num_bytes > n_lanes.U) {
                        num_bytes   := num_bytes - n_lanes.U
                    }
                }
             } // sDATA
             is (sRESP) {
                 num_bytes  := 0.U
             } // sRESP
         } // switch (state)

         //
         // WSTRB
         //
         val choices = VecInit((0 until n_lanes).map(x => (1 << x) - 1).map(_.U))
         when (state === sDATA) {
             when (num_bytes >= n_lanes.U) {
                 w_strb  := Fill(n_lanes, 1.U(1.W))
             }.otherwise {
                 w_strb  := choices(num_bytes(log2(n_lanes)-1, 0))
             }
         }.otherwise {
             w_strb  := 0.U
         }

         WriteIntf.W.bits.STRB  := w_strb

         //
         // WVALID
         //
         when (state === sDATA) {
             w_valid    := DataIntf.valid
         }.otherwise {
             w_valid    := false.B
         }

         WriteIntf.W.valid  := w_valid

         //
         // WLAST
         //
         when (state === sDATA) {
             w_last     := num_bytes <= n_lanes.U
         }.otherwise {
             w_last     := false.B
         }

         WriteIntf.W.bits.LAST  := w_last

         //
         // Internal Signals
         //
         last_beat  := WriteIntf.W.valid && WriteIntf.W.ready && WriteIntf.W.bits.LAST
         aw_len      := ((CmdIntf.bits.NumBytes >> aw_size.U) +
                          CmdIntf.bits.NumBytes(aw_size-1, 0).orR) - 1.U

         //
         // BREADY
         //
         WriteIntf.B.ready  := state === sRESP

         //
         // Status Signals
         //
         StatIntf.valid     := state === sSTAT
         StatIntf.bits      := write_resp

         //
         // Command Interface Ready
         //
         CmdIntf.ready  := state === sIDLE

         //
         // Data Interface Ready
         //
         DataIntf.ready := (state === sDATA) && WriteIntf.W.ready

     } // withClockAndReset(ACLK, reset)
}

/**
 * Provides the states for the [[Writer]] finite state machine
 */
object Writer {

    /**
     * States for Writer FSM
     */
    object State extends ChiselEnum {
        val sIDLE, sADDR, sDATA, sRESP, sSTAT = Value
    } // object State
} // object Writer
