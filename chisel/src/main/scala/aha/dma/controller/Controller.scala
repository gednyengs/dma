// =============================================================================
// filename     : Controller.scala
// description  : DMA Contoller
// author       : Gedeon Nyengele
// =============================================================================
//
// Assumptions:
//  - Transfer address is aligned to the bus width
//
// TO-DO:
//  - Add support for ABORT
//
// =============================================================================

package aha
package dma

// Chisel Imports
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{Decoupled, is, switch}

// Project Imports
import aha.dma.CmdBundle
import aha.dma.util.AXILiteIntf

//
// Controller
//
class Controller[R <: CmdBundle, W <: CmdBundle] (
    AddrWidth   : Int,
    DataWidth   : Int,
    RdCmd       : R,
    WrCmd       : W,
    MagicID     : Int = 0x5A5A5A5A ) extends RawModule {

    import Controller.State
    import Controller.State._

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK        = IO(Input(Clock()))
    val ARESETn     = IO(Input(Bool()))

    // Interrupt
    val Irq         = IO(Output(Bool()))

    // AXI-Lite Register Interface
    val RegIntf     = IO(Flipped(new AXILiteIntf(32, 32)))

    // Read Command/Status Interfaces
    val RdCmdIntf   = IO(Decoupled(RdCmd))
    val RdStatIntf  = IO(Flipped(Decoupled(UInt(2.W))))

    // Write Command/Status Interfaces
    val WrCmdIntf   = IO(Decoupled(WrCmd))
    val WrStatIntf  = IO(Flipped(Decoupled(UInt(2.W))))

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset           = (!ARESETn).asAsyncReset

    // =========================================================================
    // Internal Logic
    // =========================================================================

    val state           = withClockAndReset(ACLK, reset) { RegInit(sIDLE) }

    //
    // Control Signals (from RegFile)
    //
    val go_pulse        = Wire(Bool())
    val irq_enable      = Wire(Bool())
    val irq_clear_pulse = Wire(Bool())
    val src_addr        = Wire(UInt(32.W))
    val dst_addr        = Wire(UInt(32.W))
    val length          = Wire(UInt(32.W))

    //
    // Status Signals (to RegFile)
    //
    val busy            = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val irq_status      = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val stat_code       = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }

    //
    // Instantiate RegFile
    //
    val reg_file        = Module(new RegFile(MagicID))

    reg_file.ACLK       := ACLK
    reg_file.ARESETn    := ARESETn
    reg_file.RegIntf    <> RegIntf
    reg_file.Busy       := busy
    reg_file.IRQStatus  := irq_status
    reg_file.StatCode   := stat_code
    go_pulse            := reg_file.GO_Pulse
    irq_enable          := reg_file.IRQEnable
    irq_clear_pulse     := reg_file.IRQClear_Pulse
    src_addr            := reg_file.SrcAddr
    dst_addr            := reg_file.DstAddr
    length              := reg_file.Length

    //
    // Instantiate Splitter for Read Engine
    //
    val rd_trans_cmd_ready          = Wire(Bool())
    val rd_trans_cmd_valid          = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val rd_trans_cmd_bits           = withClockAndReset(ACLK, reset) {
                                        RegInit(0.U.asTypeOf(new TransBundle(AddrWidth)))
                                      }
    val rd_trans_stat_ready         = Wire(Bool())
    val rd_trans_stat_valid         = Wire(Bool())
    val rd_trans_stat_bits          = Wire(UInt(2.W))

    val rd_splitter                 = Module(   new Splitter(
                                                    new TransBundle(AddrWidth),
                                                    new CmdBundle(AddrWidth),
                                                    AddrWidth,
                                                    DataWidth
                                                )
                                            )

    rd_splitter.ACLK                := ACLK
    rd_splitter.ARESETn             := ARESETn
    rd_trans_cmd_ready              := rd_splitter.TransCmdIntf.ready
    rd_splitter.TransCmdIntf.valid  := rd_trans_cmd_valid
    rd_splitter.TransCmdIntf.bits   := rd_trans_cmd_bits
    rd_splitter.TransStatIntf.ready := rd_trans_stat_ready
    rd_trans_stat_valid             := rd_splitter.TransStatIntf.valid
    rd_trans_stat_bits              := rd_splitter.TransStatIntf.bits
    rd_splitter.XferCmdIntf         <> RdCmdIntf
    rd_splitter.XferStatIntf        <> RdStatIntf

    //
    // Instantiate Splitter for Write Engine
    //
    val wr_trans_cmd_ready          = Wire(Bool())
    val wr_trans_cmd_valid          = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val wr_trans_cmd_bits           = withClockAndReset(ACLK, reset) {
                                        RegInit(0.U.asTypeOf(new TransBundle(AddrWidth)))
                                      }
    val wr_trans_stat_ready         = Wire(Bool())
    val wr_trans_stat_valid         = Wire(Bool())
    val wr_trans_stat_bits          = Wire(UInt(2.W))

    val wr_splitter                 = Module(   new Splitter(
                                                    new TransBundle(AddrWidth),
                                                    new CmdBundle(AddrWidth),
                                                    AddrWidth,
                                                    DataWidth
                                                )
                                            )

    wr_splitter.ACLK                := ACLK
    wr_splitter.ARESETn             := ARESETn
    wr_trans_cmd_ready              := wr_splitter.TransCmdIntf.ready
    wr_splitter.TransCmdIntf.valid  := wr_trans_cmd_valid
    wr_splitter.TransCmdIntf.bits   := wr_trans_cmd_bits
    wr_splitter.TransStatIntf.ready := wr_trans_stat_ready
    wr_trans_stat_valid             := wr_splitter.TransStatIntf.valid
    wr_trans_stat_bits              := wr_splitter.TransStatIntf.bits
    wr_splitter.XferCmdIntf         <> WrCmdIntf
    wr_splitter.XferStatIntf        <> WrStatIntf

    //
    // Internal Signals
    //
    val read_cmd_posted     = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val write_cmd_posted    = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val read_stat_posted    = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val write_stat_posted   = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val read_stat           = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W))}
    val write_stat          = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W))}
    val err_stat            = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W))}

    withClockAndReset(ACLK, reset) {

        //
        // State Register Update Logic
        //
        switch (state) {
            is (sIDLE) {
                when (go_pulse && (length === 0.U)) {
                    state   := sSTAT
                }.elsewhen (go_pulse) {
                    state   := sPOST
                }
            } // sIDLE
            is (sPOST) {
                when (read_cmd_posted && write_cmd_posted) {
                    state   := sSTAT
                }
            } // sPOST
            is (sSTAT) {
                when (read_stat_posted && write_stat_posted) {
                    state   := sIDLE
                }
            } // sSTAT
        } // switch (state)

        //
        // Interrupt Logic
        //
        Irq     := irq_status && irq_enable

        //
        // Error Status Code
        //
        switch (state) {
            is (sIDLE) {
                when (go_pulse && (length === 0.U)) {
                    err_stat    := 2.U
                }.otherwise {
                    err_stat    := 0.U
                }
            } // sIDLE
        } // switch (state)

        //
        // Busy Signal
        // (Can be made un-registered to avoid 1 cycle delay if necessary)
        //
        busy    := state =/= sIDLE

        //
        // IRQ Status
        //
        when ((state === sSTAT) && read_stat_posted && write_stat_posted) {
            irq_status  := true.B
        }.elsewhen (irq_clear_pulse) {
            irq_status  := false.B
        }

        //
        // Transaction Status Code
        //
        switch (state) {
            is (sSTAT) {
                when (read_stat_posted && write_stat_posted) {
                    when (err_stat =/= 0.U) {
                        stat_code   := err_stat
                    }.elsewhen (read_stat =/= 0.U) {
                        stat_code   := read_stat
                    }.otherwise {
                        stat_code   := write_stat
                    }
                }
            } // sSTAT
        } // switch (state)

        //
        // Read Status
        // (read_stat and read_stat_posted)
        //
        switch (state) {
            is (sIDLE) {
                read_stat               := 0.U
                read_stat_posted        := false.B
            } // sIDLE
            is (sSTAT) {
                when (rd_trans_stat_valid || (err_stat =/= 0.U)) {
                    read_stat           := rd_trans_stat_bits
                    read_stat_posted    := true.B
                }
            } // sSTAT
        } // switch (state)

        rd_trans_stat_ready := state === sSTAT

        //
        // Write Status
        // (write_stat and write_stat_posted)
        //
        switch (state) {
            is (sIDLE) {
                write_stat               := 0.U
                write_stat_posted        := false.B
            } // sIDLE
            is (sSTAT) {
                when (wr_trans_stat_valid || (err_stat =/= 0.U)) {
                    write_stat           := wr_trans_stat_bits
                    write_stat_posted    := true.B
                }
            } // sSTAT
        } // switch (state)

        wr_trans_stat_ready := state === sSTAT

        //
        // Read Transaction Command
        //
        switch (state) {
            is (sIDLE) {
                read_cmd_posted                 := false.B
                when (go_pulse && (length =/= 0.U)) {
                    rd_trans_cmd_valid          := true.B
                    rd_trans_cmd_bits.NumBytes  := length
                    rd_trans_cmd_bits.Address   := src_addr
                }.otherwise {
                    rd_trans_cmd_valid          := false.B
                }
            } // sIDLE
            is (sPOST) {
                when (rd_trans_cmd_ready) {
                    read_cmd_posted             := true.B
                    rd_trans_cmd_valid          := false.B
                }
            } // sPOST
        } // switch (state)

        //
        // Write Transaction Command
        //
        switch (state) {
            is (sIDLE) {
                write_cmd_posted                := false.B
                when (go_pulse && (length =/= 0.U)) {
                    wr_trans_cmd_valid          := true.B
                    wr_trans_cmd_bits.NumBytes  := length
                    wr_trans_cmd_bits.Address   := dst_addr
                }.otherwise {
                    wr_trans_cmd_valid          := false.B
                }
            } // sIDLE
            is (sPOST) {
                when (wr_trans_cmd_ready) {
                    write_cmd_posted            := true.B
                    wr_trans_cmd_valid          := false.B
                }
            } // sPOST
        } // switch (state)



    } // withClockAndReset(ACLK, reset)

} // class Controller

//
// Companion Object
//
object Controller {

    //
    // States for Controller FSM
    //
    object State extends ChiselEnum {
        val sIDLE, sPOST, sSTAT = Value
    }
} // object Controller
