package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.util.isPow2

/* Project Imports */
import aha.dma.CmdBundle
import aha.dma.util.{AXI4Intf, AXILiteIntf}

/**
 * DMA Top-Level Module
 *
 * @constructor         constructs a DMA engine with an AXI ID width, address bus
 *                      width, data bus width, store-and-forward fifo depth, and
 *                      the value of the peripheral ID register
 * @param IdWidth       the width of AXI ID signals (AWID, BID, ARID, and RID)
 * @param AddrWidth     the width of AXI address busses (AWADDR and ARADDR)
 * @param DataWidth     the width of AXI data busses (WDATA and RDATA)
 * @param FifoDepth     the depth of the store-and-forward fifo
 * @param RegFileName   the name of the register file type to instantiate
 * @param MagicID       the ID value to read from the ID_REG register
 *
 * @note transfer addresses must be aligned to the data bus width (DataWidth)
 * @note FifoDepth must be a power of 2
 */
class DMA ( IdWidth     : Int,
            AddrWidth   : Int,
            DataWidth   : Int,
            FifoDepth   : Int,
            RegFileName : String,
            MagicID     : Int = 0x5A5A5A5A ) extends RawModule {

    // FifoDepth must be a power of 2
    require(isPow2(FifoDepth))

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Rese
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // Interrupt
    val Irq             = IO(Output(Bool()))

    // AXI-Lite Register Interface
    lazy val RegIntf    = IO(Flipped(controller.reg_file.getRegFileIntf))

    // AXI4 Interface
    val M_AXI           = IO(new AXI4Intf(IdWidth, AddrWidth, DataWidth))

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
                                    RegFileName,
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

    //
    // Change Top-Level Name of the Register File Interface Bundle
    //
    RegIntf.suggestName("RegIntf")

} // class DMA
