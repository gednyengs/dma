package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Decoupled, is, isPow2, switch}

/* Project Imports */
import aha.dma.{CmdBundle, log2 , MAX_BURST_BYTES, MAX_BURST_LENGTH, TransBundle}

/**
 * Transaction Splitter (splits a transaction into transfers)
 *
 * @constructor         constructs a transaction splitter with the provided
 *                      bundle type for transaction, bundle type for transfers,
 *                      address bus width and data bus width
 * @param TransCmd      bundle type for transaction commands
 * @param XferCmd       bundle type for transfer commands
 * @param AddrWidth     the width of AXI address busses (AWADDR and ARADDR)
 * @param DataWidth     the width of AXI data busses (WDATA and RDATA)
 *
 * @note transfer addresses must be aligned to the data bus width (DataWidth)
 */
class Splitter[C1 <: TransBundle, C2 <: CmdBundle] (
    TransCmd    : C1,
    XferCmd     : C2,
    AddrWidth   : Int,
    DataWidth   : Int ) extends RawModule {

    import Splitter.State
    import Splitter.State._

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // Transaction Cmd/Status Interface
    val TransCmdIntf    = IO(Flipped(Decoupled(TransCmd)))
    val TransStatIntf   = IO(Decoupled(UInt(2.W)))

    // Transfer Cmd/Status Interface
    val XferCmdIntf     = IO(Decoupled(XferCmd))
    val XferStatIntf    = IO(Flipped(Decoupled(UInt(2.W))))

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset       = (!ARESETn).asAsyncReset

    // =========================================================================
    // Internal Logic
    // =========================================================================

    // Maximum Number of Bytes in a Transfer
    val n_lanes             = DataWidth / 8
    val max_link_bytes      = MAX_BURST_LENGTH * n_lanes
    val max_xfer_bytes      = if (max_link_bytes < MAX_BURST_BYTES)
                                max_link_bytes
                              else
                              MAX_BURST_BYTES
    val max_xfer_bytes_log  = log2(max_xfer_bytes)

    // max_xfer_bytes must be a power of 2
    require (isPow2(max_xfer_bytes))

    val state           = withClockAndReset(ACLK, reset) { RegInit(sIDLE) }
    val cur_num_bytes   = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val rem_bytes       = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val bBytes          = max_xfer_bytes.U - TransCmdIntf.bits.Address(max_xfer_bytes_log - 1, 0)
    val cur_addr        = withClockAndReset(ACLK, reset) { RegInit(0.U(AddrWidth.W)) }
    val next_addr       = ((cur_addr >> max_xfer_bytes_log.U) + 1.U) << max_xfer_bytes_log.U
    val trans_status    = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }


    withClockAndReset(ACLK, reset) {
        //
        // State Register Update Logic
        //
        switch (state) {
            is (sIDLE) {
                when (TransCmdIntf.valid) {
                    state   := sSPLIT
                }
            } // sIDLE
            is (sSPLIT) {
                when (XferCmdIntf.ready) {
                    state   := sWAIT
                }
            } // sSPLIT
            is (sWAIT) {
                when (XferStatIntf.valid) {
                    when (rem_bytes > 0.U) {
                        state   := sSPLIT
                    }.otherwise {
                        state   := sSTAT
                    }
                }
            } // sWAIT
            is (sSTAT) {
                when (TransStatIntf.ready) {
                    state   := sIDLE
                }
            } // sSTAT
        } // switch(state)

        //
        // Transaction Address Splitting
        //
        switch (state) {
            is (sIDLE) {
                when (TransCmdIntf.valid) {
                    cur_addr    := TransCmdIntf.bits.Address
                }
            } // sIDLE
            is (sWAIT) {
                when (XferStatIntf.valid) {
                    cur_addr    := next_addr
                }
            }
        } // switch (state)

        //
        // Transaction Size (bytes count) Splitting
        //
        switch (state) {
            is (sIDLE) {
                when (TransCmdIntf.valid) {
                    when (bBytes <= TransCmdIntf.bits.NumBytes) {
                        cur_num_bytes   := bBytes
                        rem_bytes       := TransCmdIntf.bits.NumBytes - bBytes
                    }.otherwise {
                        cur_num_bytes   := TransCmdIntf.bits.NumBytes
                        rem_bytes       := 0.U
                    }
                }
            } // sIDLE
            is (sWAIT) {
                when (XferStatIntf.valid) {
                    when (rem_bytes > max_xfer_bytes.U) {
                        cur_num_bytes   := max_xfer_bytes.U
                        rem_bytes       := rem_bytes - max_xfer_bytes.U
                    }.otherwise {
                        cur_num_bytes   := rem_bytes
                        rem_bytes       := 0.U
                    }
                }
            } // sWAIT
        } // switch (state)

        //
        // Transaction Status
        //
        switch (state) {
            is (sIDLE) {
                trans_status    := 0.U
            } // sIDLE
            is (sWAIT) {
                when (XferStatIntf.valid && (TransStatIntf.bits === 0.U)) {
                    trans_status    := XferStatIntf.bits
                }
            }
        } // switch (state)

        TransStatIntf.bits          := trans_status

        //
        // Transaction Cmd/Status
        //
        TransCmdIntf.ready          := state === sIDLE
        TransStatIntf.valid         := state === sSTAT

        //
        // Transfer Status Ready
        //
        XferStatIntf.ready          := state === sWAIT

        //
        // Transfer Command
        //
        XferCmdIntf.valid           := state === sSPLIT
        XferCmdIntf.bits.NumBytes   := cur_num_bytes
        XferCmdIntf.bits.Address    := cur_addr

    } // withClockAndReset(ACLK, reset)

} // class Splitter

/**
 * Provides the states for the [[Splitter]] finite state machine
 */
object Splitter {

    /**
     * States for Splitter FSM
     */
    object State extends ChiselEnum {
        val sIDLE, sSPLIT, sWAIT, sSTAT = Value
    } // object State

} // object Splitter
