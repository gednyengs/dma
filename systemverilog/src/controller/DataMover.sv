// =============================================================================
// filename     : DataMover.sv
// description  : DataMover is a Reader-Writer Combo
// author       : Gedeon Nyengele
// =============================================================================
//
// Assumptions:
//  - Transfer addresses are 8-byte-aligned
//
// TO-DO:
//  - Add support for ABORT
// =============================================================================
module DataMover #(parameter FIFO_DEPTH = 256) (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Reader Cmd/Stat Interface
    ReadyValidIntf.Slave    RdCmdIntf,
    ReadyValidIntf.Master   RdStatIntf,

    // Writer Cmd/Stat Interface
    ReadyValidIntf.Slave    WrCmdIntf,
    ReadyValidIntf.Master   WrStatIntf,

    // AXI4 Read Interface
    AXI4ReadIntf.Master     ReadIntf,

    // AXI4 Write Interface
    AXI4WriteIntf.Master    WriteIntf
);

    import DmaPkg::Packet_t;

    ReadyValidIntf #(.DataTy(Packet_t))     fifoEnqIntf();
    ReadyValidIntf #(.DataTy(Packet_t))     fifoDeqIntf();

    Reader reader_inst (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .CmdIntf        (RdCmdIntf),
        .StatIntf       (RdStatIntf),
        .DataIntf       (fifoEnqIntf),
        .ReadIntf       (ReadIntf)
    );

    PeekQueue #(.DEPTH(FIFO_DEPTH)) peek_queue_inst (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .Abort          (1'b0),
        .EnqIntf        (fifoEnqIntf),
        .DeqIntf        (fifoDeqIntf)
    );

    Writer writer_inst (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .CmdIntf        (WrCmdIntf),
        .StatIntf       (WrStatIntf),
        .DataIntf       (fifoDeqIntf),
        .WriteIntf      (WriteIntf)
    );

endmodule;
