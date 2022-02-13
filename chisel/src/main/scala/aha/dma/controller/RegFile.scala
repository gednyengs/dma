// =============================================================================
// filename     : RegFile.scala
// description  : Controller Register File (A La Xilinx AXI4 Peripherals)
// author       : Gedeon Nyengele
// =============================================================================

package aha
package dma

// Chisel Imports
import chisel3._
import chisel3.util.{Cat, is, switch}

// Project Imports
import aha.dma.util.AXILiteIntf

//
// Register File
//
class RegFile(MagicID: Int = 0x5A5A5A5A) extends RawModule {

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // AXI-Lite Interface
    val RegIntf         = IO(Flipped(new AXILiteIntf(32, 32)))

    // Control Signals
    val GO_Pulse        = IO(Output(Bool()))
    val IRQEnable       = IO(Output(Bool()))
    val IRQClear_Pulse  = IO(Output(Bool()))
    val SrcAddr         = IO(Output(UInt(32.W)))
    val DstAddr         = IO(Output(UInt(32.W)))
    val Length          = IO(Output(UInt(32.W)))

    // Status Signals
    val Busy            = IO(Input(Bool()))
    val IRQStatus       = IO(Input(Bool()))
    val StatCode        = IO(Input(UInt(2.W)))

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset       = (!ARESETn).asAsyncReset

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
    val axi_awaddr          = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val axi_awready         = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val aw_en               = withClockAndReset(ACLK, reset) { RegInit(true.B) }
    val axi_wready          = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val axi_bvalid          = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val axi_bresp           = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }
    val axi_araddr          = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val axi_arready         = withClockAndReset(ACLK, reset) { RegInit(false.B) }
    val axi_rdata           = withClockAndReset(ACLK, reset) { RegInit(0.U(32.W)) }
    val axi_rresp           = withClockAndReset(ACLK, reset) { RegInit(0.U(2.W)) }
    val axi_rvalid          = withClockAndReset(ACLK, reset) { RegInit(false.B) }

    val slv_reg_rden        = Wire(Bool())
    val slv_reg_wren        = Wire(Bool())
    val reg_data_out        = Wire(UInt(32.W))

    //
    // AXI-Lite Connections
    //
    RegIntf.AWREADY         := axi_awready
    RegIntf.WREADY          := axi_wready
    RegIntf.BRESP           := axi_bresp
    RegIntf.BVALID          := axi_bvalid
    RegIntf.ARREADY         := axi_arready
    RegIntf.RDATA           := axi_rdata
    RegIntf.RRESP           := axi_rresp
    RegIntf.RVALID          := axi_rvalid

    withClockAndReset(ACLK, reset) {

        //
        // axi_awready and aw_en
        //
        when (~axi_awready && RegIntf.AWVALID && RegIntf.WVALID && aw_en) {
            axi_awready := true.B
            aw_en       := false.B
        }.elsewhen (RegIntf.BREADY && axi_bvalid) {
            axi_awready := false.B
            aw_en       := true.B
        }.otherwise {
            axi_awready := false.B
        }

        //
        // axi_awaddr
        //
        when (~axi_awready && RegIntf.AWVALID && RegIntf.WVALID && aw_en) {
            axi_awaddr  := RegIntf.AWADDR
        }

        //
        // axi_wready
        //
        when (~axi_wready && RegIntf.WVALID && RegIntf.AWVALID && aw_en) {
            axi_wready  := true.B
        }.otherwise {
            axi_wready  := false.B
        }

        //
        // axi_bvalid and axi_bresp
        //
        when (axi_awready && RegIntf.AWVALID && ~axi_bvalid && axi_wready && RegIntf.WVALID) {
            axi_bvalid  := true.B
            axi_bresp   := 0.U
        }.elsewhen (RegIntf.BREADY && axi_bvalid) {
            axi_bvalid  := false.B
        }

        //
        // Write Enable Signal
        //
        slv_reg_wren    := axi_awready && RegIntf.AWVALID && axi_wready && RegIntf.WVALID

        //
        // Generate GO Pulse
        //
        when (slv_reg_wren && (axi_awaddr(4, 2) === 0.U) && (RegIntf.WSTRB(0) === 1.U)) {
            reg_go_pulse    := RegIntf.WDATA(0).asBool
        }.otherwise {
            reg_go_pulse    := false.B
        }

        GO_Pulse    := reg_go_pulse

        //
        // Generate Interrupt Clear Pulse
        //
        when (slv_reg_wren && (axi_awaddr(4, 2) === 2.U) && (RegIntf.WSTRB(0) === 1.U)) {
            reg_intclr_pulse    := RegIntf.WDATA(1).asBool
        }.otherwise {
            reg_intclr_pulse    := false.B
        }

        IRQClear_Pulse  := reg_intclr_pulse

        //
        // Interrupt Enable Register Update Logic
        //
        when (slv_reg_wren && (axi_awaddr(4, 2) === 0.U) && (RegIntf.WSTRB(0) === 1.U)) {
            reg_ie  := RegIntf.WDATA(1).asBool
        }

        IRQEnable   := reg_ie

        //
        // Source Address Register
        //
        val reg_src_addr_v = Wire(Vec(4, UInt(8.W)))
        reg_src_addr_v := reg_src_addr.asTypeOf(Vec(4, UInt(8.W)))

        when (slv_reg_wren && (axi_awaddr(4, 2) === 3.U)) {
            for (n <- 0 to 3) {
                when (RegIntf.WSTRB(n) === 1.U) {
                    reg_src_addr_v(n)   := RegIntf.WDATA(n*8+7, n*8)
                }
            }
            reg_src_addr    := reg_src_addr_v.asTypeOf(UInt(32.W))
        }

        SrcAddr := reg_src_addr

        //
        // Destination Address Register
        //
        val reg_dst_addr_v = Wire(Vec(4, UInt(8.W)))
        reg_dst_addr_v := reg_dst_addr.asTypeOf(Vec(4, UInt(8.W)))

        when (slv_reg_wren && (axi_awaddr(4, 2) === 4.U)) {
            for (n <- 0 to 3) {
                when (RegIntf.WSTRB(n) === 1.U) {
                    reg_dst_addr_v(n)   := RegIntf.WDATA(n*8+7, n*8)
                }
            }
            reg_dst_addr    := reg_dst_addr_v.asTypeOf(UInt(32.W))
        }

        DstAddr := reg_dst_addr

        //
        // Length Register
        //
        val reg_length_v    = Wire(Vec(4, UInt(8.W)))
        reg_length_v        := reg_length.asTypeOf(Vec(4, UInt(8.W)))

        when (slv_reg_wren && (axi_awaddr(4, 2) === 5.U)) {
            for (n <- 0 to 3) {
                when (RegIntf.WSTRB(n) === 1.U) {
                    reg_length_v(n)   := RegIntf.WDATA(n*8+7, n*8)
                }
            }
            reg_length    := reg_length_v.asTypeOf(UInt(32.W))
        }

        Length  := reg_length

        //
        // axi_arready and axi_araddr
        //
        when (~axi_arready && RegIntf.ARVALID) {
            axi_arready := true.B
            axi_araddr  := RegIntf.ARADDR
        }.otherwise {
            axi_arready := false.B
        }

        //
        // axi_rvalid and axi_rresp
        //
        when (axi_arready && RegIntf.ARVALID && ~axi_rvalid) {
            axi_rvalid  := true.B
            axi_rresp   := 0.U
        }.elsewhen (axi_rvalid && RegIntf.RREADY) {
            axi_rvalid  := false.B
        }

        //
        // Read Enable Signal
        //
        slv_reg_rden    := axi_arready && RegIntf.ARVALID && ~axi_rvalid

        //
        // Output Select Logic
        //
        reg_data_out    := 0.U
        switch (axi_araddr(4, 2)) {
            is (0.U) { reg_data_out := Cat(reg_ie, 0.U(1.W)) }
            is (1.U) { reg_data_out := Cat(StatCode, IRQStatus.asUInt, Busy.asUInt) }
            is (3.U) { reg_data_out := reg_src_addr }
            is (4.U) { reg_data_out := reg_dst_addr }
            is (5.U) { reg_data_out := reg_length }
            is (6.U) { reg_data_out := MagicID.U }
        }

        //
        // axi_rdata
        //
        when (slv_reg_rden) {
            axi_rdata   := reg_data_out
        }

    } // withClockAndReset(ACLK, reset)
}
