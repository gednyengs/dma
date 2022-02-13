// =============================================================================
// filename     : DMA.sv
// description  : DMA Top-Level
// author       : Gedeon Nyengele
// =============================================================================
//
// Rules:
//  -   Parameter `FIFO_DEPTH` must be a power of 2
//  -   Addresses must be 8-byte-aligned
//
// TO-DO:
//  - Support for ABORT condition
// =============================================================================
module DMA #(parameter FIFO_DEPTH = 256)
(
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Interrupt
    output  logic           Irq,

    // Controller Access Port
    AXILiteIntf.Slave       RegIntf,

    // AXI4 Reader Port
    AXI4ReadIntf.Master     AxiReadIntf,

    // AXI4 Writer Port
    AXI4WriteIntf.Master    AxiWriteIntf
);

    import DmaPkg::Cmd_t;

    ReadyValidIntf #(.DataTy(Cmd_t))        rd_cmd_intf();
    ReadyValidIntf #(.DataTy(logic[1:0]))   rd_stat_intf();

    ReadyValidIntf #(.DataTy(Cmd_t))        wr_cmd_intf();
    ReadyValidIntf #(.DataTy(logic[1:0]))   wr_stat_intf();

    Controller controller_inst (
        .ACLK               (ACLK),
        .ARESETn            (ARESETn),
        .Irq                (Irq),
        .RegIntf            (RegIntf),
        .RdCmdIntf          (rd_cmd_intf),
        .RdStatIntf         (rd_stat_intf),
        .WrCmdIntf          (wr_cmd_intf),
        .WrStatIntf         (wr_stat_intf)
    );

    DataMover #(.FIFO_DEPTH(FIFO_DEPTH)) data_mover_inst (
        .ACLK               (ACLK),
        .ARESETn            (ARESETn),
        .RdCmdIntf          (rd_cmd_intf),
        .RdStatIntf         (rd_stat_intf),
        .WrCmdIntf          (wr_cmd_intf),
        .WrStatIntf         (wr_stat_intf),
        .ReadIntf           (AxiReadIntf),
        .WriteIntf          (AxiWriteIntf)
    );

endmodule
