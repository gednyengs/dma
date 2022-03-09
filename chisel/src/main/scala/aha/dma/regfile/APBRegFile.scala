package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.util.{Cat, is, switch}

/* Project Imports */
import aha.dma.util.APBIntf

/**
 * DMA Register File with APB interface
 *
 * @constructor         constructs a register file with the provided value for
 *                      the ID register
 * @param MagicID       the value for the ID register
 */
class APBRegFile(val MagicID: Int = 0x5A5A5A5A) extends RegFile {

    // Type of Register File Interface
    type RegIntfTy      = APBIntf

    // AXI-Lite Interface
    def getRegFileIntf  = new APBIntf(32, 32)

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset           = (!ARESETn).asAsyncReset

    // =========================================================================
    // Internal Logic
    // =========================================================================

    //
    // Registers
    //
    val reg_go_pulse        = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val reg_ie              = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val reg_intclr_pulse    = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val reg_src_addr        = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val reg_dst_addr        = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val reg_length          = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }

    //
    // Internal Signals
    //
    val prdata              = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val pready              = Wire(Bool())
    val pslverr             = withClockAndReset(ACLK, reset) { RegInit(false.B) }

    val reg_data_out        = Wire(UInt(32.W))
    val setup_phase         = Wire(Bool())
    val wr_en               = Wire(Bool())
    val rd_en               = Wire(Bool())

    setup_phase             := RegIntf.PSEL && ~RegIntf.PENABLE
    wr_en                   := setup_phase && RegIntf.PWRITE
    rd_en                   := setup_phase && ~RegIntf.PWRITE

    //
    // APB Connections
    //
    RegIntf.PRDATA          := prdata
    RegIntf.PREADY          := pready
    RegIntf.PSLVERR         := pslverr


    withClockAndReset(ACLK, reset) {

        //
        // Generate GO Pulse
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 0.U)) {
            reg_go_pulse    := RegIntf.PWDATA(0).asBool
        }.otherwise {
            reg_go_pulse    := false.B
        }

        GO_Pulse    := reg_go_pulse

        //
        // Generate Interrupt Clear Pulse
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 2.U)) {
            reg_intclr_pulse    := RegIntf.PWDATA(1).asBool
        }.otherwise {
            reg_intclr_pulse    := false.B
        }

        IRQClear_Pulse  := reg_intclr_pulse

        //
        // Interrupt Enable Register Update Logic
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 0.U)) {
            reg_ie  := RegIntf.PWDATA(1).asBool
        }

        IRQEnable   := reg_ie

        //
        // Source Address Register
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 3.U)) {
            reg_src_addr  := RegIntf.PWDATA
        }

        SrcAddr := reg_src_addr

        //
        // Destination Address Register
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 4.U)) {
            reg_dst_addr  := RegIntf.PWDATA
        }

        DstAddr := reg_dst_addr

        //
        // Length Register
        //
        when (wr_en && (RegIntf.PADDR(4, 2) === 5.U)) {
            reg_length  := RegIntf.PWDATA
        }

        Length := reg_length

        //
        // Output Data Select Logic
        //
        reg_data_out    := 0.U
        switch (RegIntf.PADDR(4, 2)) {
            is (0.U) { reg_data_out := Cat(reg_ie, 0.U(1.W)) }
            is (1.U) { reg_data_out := Cat(StatCode, IRQStatus.asUInt, Busy.asUInt) }
            is (3.U) { reg_data_out := reg_src_addr }
            is (4.U) { reg_data_out := reg_dst_addr }
            is (5.U) { reg_data_out := reg_length }
            is (6.U) { reg_data_out := MagicID.U }
        }

        //
        // prdata
        //
        when (rd_en) {
            prdata   := reg_data_out
        }

        //
        // pready
        //
        pready  := true.B // Always Ready

        //
        // pslverr
        //
        when (rd_en) {
            when (RegIntf.PADDR(4, 2) > 6.U) {
                pslverr := true.B
            }.otherwise {
                pslverr := false.B
            }
        }.otherwise {
            pslverr := false.B
        }

    } // withClockAndReset(ACLK, reset)
} // class RegFile
