// =============================================================================
// filename     : Controller.scala
// description  : DMA Top-Level
// author       : Gedeon Nyengele
// =============================================================================
//
// Assumptions:
//  - Transfer address is aligned to the bus width
//  - FIFO_DEPTH must be a power of 2
//
// TO-DO:
//  - Add support for ABORT
//
// =============================================================================

package aha
package dma

// Chisel Imports
import chisel3._

// Project Imports
import aha.dma.CmdBundle
import aha.dma.util.{AXI4Intf, AXILiteIntf}

//
// DMA Top-Level Module
//
class DMA ( IdWidth     : Int,
            AddrWidth   : Int,
            DataWidth   : Int,
            FifoDepth   : Int,
            MagicID     : Int = 0x5A5A5A5A ) extends RawModule {

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // Interrupt
    val Irq         = IO(Output(Bool()))

    // AXI-Lite Register Interface
    val RegIntf     = IO(Flipped(new AXILiteIntf(32, 32)))

    // AXI4 Interface
    val M_AXI       = IO(new AXI4Intf(IdWidth, AddrWidth, DataWidth))

    // =========================================================================
    // Internal Logic
    // =========================================================================

    //
    // Instantiate Controller
    //
    val controller  = Module(   new Controller(
                                    AddrWidth,
                                    DataWidth,
                                    new CmdBundle(AddrWidth),
                                    new CmdBundle(AddrWidth),
                                    MagicID
                                )
                            )

    //
    // Instantiate DataMover
    //
    val data_mover  = Module(   new DataMover(
                                    IdWidth,
                                    AddrWidth,
                                    DataWidth,
                                    new CmdBundle(AddrWidth),
                                    new CmdBundle(AddrWidth),
                                    FifoDepth
                                )
                            )

    //
    // Connections
    //

    controller.ACLK         := ACLK
    controller.ARESETn      := ARESETn
    controller.RegIntf      <> RegIntf
    Irq                     := controller.Irq

    data_mover.ACLK         := ACLK
    data_mover.ARESETn      := ARESETn
    data_mover.M_AXI        <> M_AXI

    controller.RdCmdIntf    <> data_mover.RdCmdIntf
    controller.RdStatIntf   <> data_mover.RdStatIntf

    controller.WrCmdIntf    <> data_mover.WrCmdIntf
    controller.WrStatIntf   <> data_mover.WrStatIntf

} // class DMA
