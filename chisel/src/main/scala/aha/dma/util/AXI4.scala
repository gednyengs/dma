// =============================================================================
// filename     : AXI4.scala
// description  : Definition of AXI4 Interfaces and Interface Views
// author       : Gedeon Nyengele
// =============================================================================

package aha
package dma
package util

import chisel3._
import chisel3.util.Decoupled
import chisel3.experimental.dataview._

// =============================================================================
// AXI Lite Interface
// =============================================================================

class AXILiteIntf(AddrWidth: Int, DataWidth: Int) extends Bundle {

    // AW Channel
    val AWREADY     = Input(Bool())
    val AWVALID     = Output(Bool())
    val AWADDR      = Output(UInt(AddrWidth.W))
    val AWPROT      = Output(UInt(3.W))

    // W Channel
    val WREADY      = Input(Bool())
    val WVALID      = Output(Bool())
    val WDATA       = Output(UInt(DataWidth.W))
    val WSTRB       = Output(UInt((DataWidth/8).W))

    // B Channel
    val BREADY      = Output(Bool())
    val BVALID      = Input(Bool())
    val BRESP       = Input(UInt(2.W))

    // AR Channel
    val ARREADY     = Input(Bool())
    val ARVALID     = Output(Bool())
    val ARADDR      = Output(UInt(AddrWidth.W))
    val ARPROT      = Output(UInt(3.W))

    // R Channel
    val RREADY      = Output(Bool())
    val RVALID      = Input(Bool())
    val RDATA       = Input(UInt(DataWidth.W))
    val RRESP       = Input(UInt(2.W))
}

// =============================================================================
// Full AXI4 Interface Definition
// =============================================================================

class AXI4Intf(val IdWidth: Int,
               val AddrWidth: Int,
               val DataWidth: Int) extends Bundle {

    //
    // Write Address Channel
    //
    val AWID        = Output(UInt(IdWidth.W))
    val AWADDR      = Output(UInt(AddrWidth.W))
    val AWLEN       = Output(UInt(8.W))
    val AWSIZE      = Output(UInt(3.W))
    val AWBURST     = Output(UInt(2.W))
    val AWLOCK      = Output(Bool())
    val AWCACHE     = Output(UInt(4.W))
    val AWPROT      = Output(UInt(3.W))
    val AWVALID     = Output(Bool())
    val AWREADY     = Input(Bool())

    //
    // Write Data Channel
    //
    val WDATA       = Output(UInt(DataWidth.W))
    val WSTRB       = Output(UInt((DataWidth/8).W))
    val WLAST       = Output(Bool())
    val WVALID      = Output(Bool())
    val WREADY      = Input(Bool())

    //
    // Write Response Channel
    //
    val BID         = Input(UInt(IdWidth.W))
    val BRESP       = Input(UInt(2.W))
    val BVALID      = Input(Bool())
    val BREADY      = Output(Bool())

    //
    // Read Address Channel
    //
    val ARID        = Output(UInt(IdWidth.W))
    val ARADDR      = Output(UInt(AddrWidth.W))
    val ARLEN       = Output(UInt(8.W))
    val ARSIZE      = Output(UInt(3.W))
    val ARBURST     = Output(UInt(2.W))
    val ARLOCK      = Output(Bool())
    val ARCACHE     = Output(UInt(4.W))
    val ARPROT      = Output(UInt(3.W))
    val ARVALID     = Output(Bool())
    val ARREADY     = Input(Bool())

    //
    // Read Data Channel
    //
    val RID         = Input(UInt(IdWidth.W))
    val RDATA       = Input(UInt(DataWidth.W))
    val RRESP       = Input(UInt(2.W))
    val RLAST       = Input(Bool())
    val RVALID      = Input(Bool())
    val RREADY      = Output(Bool())
}


// =============================================================================
// AXI4 Write Interface View
// =============================================================================

class AXI4WrIntfView(val IdWidth: Int,
                     val AddrWidth: Int,
                     val DataWidth: Int) extends Bundle {

    val AW      = Decoupled(new AXI4AddrPayload(IdWidth, AddrWidth))
    val W       = Decoupled(new AXI4WrDataPayload(DataWidth))
    val B       = Flipped(Decoupled(new AXI4WrRespPayload(IdWidth)))
}

object AXI4WrIntfView {
    implicit val wrIntfView = PartialDataView[AXI4Intf, AXI4WrIntfView] (

        // Interface Mapping
        axi4 => new AXI4WrIntfView(axi4.IdWidth, axi4.AddrWidth, axi4.DataWidth),

        // Signal Mapping for AW Channel
        _.AWID      -> _.AW.bits.ID,
        _.AWADDR    -> _.AW.bits.ADDR,
        _.AWLEN     -> _.AW.bits.LEN,
        _.AWSIZE    -> _.AW.bits.SIZE,
        _.AWBURST   -> _.AW.bits.BURST,
        _.AWLOCK    -> _.AW.bits.LOCK,
        _.AWCACHE   -> _.AW.bits.CACHE,
        _.AWPROT    -> _.AW.bits.PROT,
        _.AWVALID   -> _.AW.valid,
        _.AWREADY   -> _.AW.ready,

        // Signal Mapping for W Channel
        _.WDATA     -> _.W.bits.DATA,
        _.WSTRB     -> _.W.bits.STRB,
        _.WLAST     -> _.W.bits.LAST,
        _.WVALID    -> _.W.valid,
        _.WREADY    -> _.W.ready,

        // Signal Mapping for B Channel
        _.BID       -> _.B.bits.ID,
        _.BRESP     -> _.B.bits.RESP,
        _.BVALID    -> _.B.valid,
        _.BREADY    -> _.B.ready
    )
}

// =============================================================================
// AXI4 Read Interface View
// =============================================================================

class AXI4RdIntfView(val IdWidth: Int,
                     val AddrWidth: Int,
                     val DataWidth: Int) extends Bundle {

    val AR  = Decoupled(new AXI4AddrPayload(IdWidth, AddrWidth))
    val R   = Flipped(Decoupled(new AXI4RdDataPayload(IdWidth, DataWidth)))
}

object AXI4RdIntfView {
    implicit val rdIntfView = PartialDataView[AXI4Intf, AXI4RdIntfView] (

        // Interface Mapping
        axi4 => new AXI4RdIntfView(axi4.IdWidth, axi4.AddrWidth, axi4.DataWidth),

        // Signal Mapping for AR Channel
        _.ARID      -> _.AR.bits.ID,
        _.ARADDR    -> _.AR.bits.ADDR,
        _.ARLEN     -> _.AR.bits.LEN,
        _.ARSIZE    -> _.AR.bits.SIZE,
        _.ARBURST   -> _.AR.bits.BURST,
        _.ARLOCK    -> _.AR.bits.LOCK,
        _.ARCACHE   -> _.AR.bits.CACHE,
        _.ARPROT    -> _.AR.bits.PROT,
        _.ARVALID   -> _.AR.valid,
        _.ARREADY   -> _.AR.ready,

        // Signal Mapping for R Channel
        _.RID       -> _.R.bits.ID,
        _.RDATA     -> _.R.bits.DATA,
        _.RRESP     -> _.R.bits.RESP,
        _.RLAST     -> _.R.bits.LAST,
        _.RVALID    -> _.R.valid,
        _.RREADY    -> _.R.ready
    )
}

// =============================================================================
// Helper Bundles
// =============================================================================

class AXI4AddrPayload(IdWidth: Int, AddrWidth: Int) extends Bundle {
    val ID      = UInt(IdWidth.W)
    val ADDR    = UInt(AddrWidth.W)
    val LEN     = UInt(8.W)
    val SIZE    = UInt(3.W)
    val BURST   = UInt(2.W)
    val LOCK    = Bool()
    val CACHE   = UInt(4.W)
    val PROT    = UInt(3.W)
}

//
// Write Address and Read Address Payload
//
class AXI4WrDataPayload(DataWidth: Int) extends Bundle {
    val DATA    = UInt(DataWidth.W)
    val STRB    = UInt((DataWidth/8).W)
    val LAST    = Bool()
}

//
// Write Data Payload
//
class AXI4WrRespPayload(IdWidth: Int) extends Bundle {
    val ID      = UInt(IdWidth.W)
    val RESP    = UInt(2.W)
}

//
// Read Data Payload
//
class AXI4RdDataPayload(IdWidth: Int, DataWidth: Int) extends Bundle {
    val ID      = UInt(IdWidth.W)
    val DATA    = UInt(DataWidth.W)
    val RESP    = UInt(2.W)
    val LAST    = Bool()
}
