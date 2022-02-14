package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.experimental.dataview._
import chisel3.util.Decoupled

/* Project Imports */
import aha.dma.CmdBundle
import aha.dma.util.{AXI4Intf, AXI4WrIntfView, AXI4RdIntfView, PeekQueue}

/**
 * AXI4 Data Mover
 *
 * @constructor         constructs a data mover with the provided AXI ID width,
 *                      address bus width, data bus width, the transfer bundle
 *                      for the reader, the transfer bundle for the
 *                      writer, and the depth of the store-and-forward fifo
 * @tparam R            transfer command bundle type for reader
 * @tparam W            transfer command bundle type for writer
 * @param IdWidth       the width of AXI ID signals (AWID, BID, ARID, and RID)
 * @param AddrWidth     the width of AXI address busses (AWADDR and ARADDR)
 * @param DataWidth     the width of AXI data busses (WDATA and RDATA)
 * @param RdCmd         the bundle to use for read transfer commands
 * @param WrCmd         the bundle to use for write transfer commands
 * @param FifoDepth     the depth of the store-and-forward fifo
 *
 * @note transfer addresses must be aligned to the data bus width (DataWidth)
 * @note transfers must not cross the [[MAX_BURST_BYTES]]
 */
class DataMover[R <: CmdBundle, W <: CmdBundle] (
    IdWidth     : Int,
    AddrWidth   : Int,
    DataWidth   : Int,
    RdCmd       : R,
    WrCmd       : W,
    FifoDepth   : Int ) extends RawModule {

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // Reader Command/Stat Interfaces
    val RdCmdIntf       = IO(Flipped(Decoupled(RdCmd)))
    val RdStatIntf      = IO(Decoupled(UInt(2.W)))

    // Writer Command/Stat Interfaces
    val WrCmdIntf       = IO(Flipped(Decoupled(WrCmd)))
    val WrStatIntf      = IO(Decoupled(UInt(2.W)))

    // AXI4 Interface
    val M_AXI           = IO(new AXI4Intf(IdWidth, AddrWidth, DataWidth))

    // =========================================================================
    // Internal Logic
    // =========================================================================

    //
    // AXI4 Read and Write Views
    //
    val axi4RdView      = M_AXI.viewAs[AXI4RdIntfView]
    val axi4WrView      = M_AXI.viewAs[AXI4WrIntfView]

    //
    // Instantiate Reader, Writer, and PeekQueue
    //
    val reader          = Module(new Reader(IdWidth, AddrWidth, DataWidth, RdCmd))
    val writer          = Module(new Writer(IdWidth, AddrWidth, DataWidth, WrCmd))
    val peekQueue       = Module(new PeekQueue(UInt(DataWidth.W), FifoDepth))

    //
    // Reader Connections
    //
    reader.ACLK         := ACLK
    reader.ARESETn      := ARESETn
    reader.CmdIntf      <> RdCmdIntf
    reader.StatIntf     <> RdStatIntf
    reader.ReadIntf     <> axi4RdView

    //
    // Writer Connections
    //
    writer.ACLK         := ACLK
    writer.ARESETn      := ARESETn
    writer.CmdIntf      <> WrCmdIntf
    writer.StatIntf     <> WrStatIntf
    writer.WriteIntf    <> axi4WrView

    //
    // PeekQueue Connections
    //
    peekQueue.ACLK      := ACLK
    peekQueue.ARESETn   := ARESETn
    peekQueue.EnqIntf   <> reader.DataIntf
    peekQueue.DeqIntf   <> writer.DataIntf
}
