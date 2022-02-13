// =============================================================================
// filename     : Controller.sv
// description  : DMA Contoller
// author       : Gedeon Nyengele
// =============================================================================
//
//
// Assumptions:
//  - Transfer addresses are 8-byte-aligned
//
// TO-DO:
//  - Add support for ABORT
// =============================================================================
module Controller (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Interrupt
    output  logic           Irq,

    // AXI-Lite Interface
    AXILiteIntf.Slave       RegIntf,

    // Read Cmd/Stat Interface
    ReadyValidIntf.Master   RdCmdIntf,
    ReadyValidIntf.Slave    RdStatIntf,

    // Write Cmd/Stat Interface
    ReadyValidIntf.Master   WrCmdIntf,
    ReadyValidIntf.Slave    WrStatIntf
);

    import DmaPkg::TransCmd_t;

    typedef enum logic [1:0] {
        IDLE        = 2'b00,
        POST        = 2'b01,
        STAT        = 2'b11,
        XXX         = 'x
    } state_e;

    // State Variables
    state_e         state, next;

    // Control Signals from RegFile
    logic           go_pulse;
    logic           irq_enable;
    logic           irq_clear_pulse;
    logic [31:0]    src_addr;
    logic [31:0]    dst_addr;
    logic [31:0]    length;

    // Status Signals to RegFile
    logic           busy;
    logic           irq_status;
    logic [1:0]     stat_code;

    // Read Engine Connections
    ReadyValidIntf #(.DataTy(TransCmd_t))   rd_trans_cmd_intf();
    ReadyValidIntf #(.DataTy(logic[1:0]))   rd_trans_stat_intf();

    // Write Engine Connections
    ReadyValidIntf #(.DataTy(TransCmd_t))   wr_trans_cmd_intf();
    ReadyValidIntf #(.DataTy(logic[1:0]))   wr_trans_stat_intf();

    // Internal Signals
    logic           read_cmd_posted;
    logic           write_cmd_posted;
    logic           read_stat_posted;
    logic           write_stat_posted;
    logic [1:0]     read_stat;
    logic [1:0]     write_stat;
    logic [1:0]     err_stat;

    //
    // RegFile Instantiation
    //
    RegFile regfile_inst (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .RegIntf        (RegIntf),
        .GO_Pulse       (go_pulse),
        .IRQEnable      (irq_enable),
        .IRQClear_Pulse (irq_clear_pulse),
        .SrcAddr        (src_addr),
        .DstAddr        (dst_addr),
        .Length         (length),
        .Busy           (busy),
        .IRQStatus      (irq_status),
        .StatCode       (stat_code)
    );

    //
    // Transaction Splitter for Read Engine
    //
    Splitter read_splitter (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .TransCmdIntf   (rd_trans_cmd_intf),
        .TransStatIntf  (rd_trans_stat_intf),
        .CmdIntf        (RdCmdIntf),
        .StatIntf       (RdStatIntf)
    );

    //
    // Transaction Splitter for Write Engine
    //
    Splitter write_splitter (
        .ACLK           (ACLK),
        .ARESETn        (ARESETn),
        .TransCmdIntf   (wr_trans_cmd_intf),
        .TransStatIntf  (wr_trans_stat_intf),
        .CmdIntf        (WrCmdIntf),
        .StatIntf       (WrStatIntf)
    );

    //
    // State Register Update
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   state <= IDLE;
        else            state <= next;

    //
    // Next State Logic
    //
    always_comb begin
        next = XXX;
        case (state)
            IDLE    :   if (go_pulse && (length == '0))
                            next = STAT;
                        else if (go_pulse)
                            next = POST;
                        else
                            next = IDLE; //@ loopback
            POST    :   if (read_cmd_posted & write_cmd_posted)
                            next = STAT;
                        else
                            next = POST; //@ loopback
            STAT    :   if (read_stat_posted & write_stat_posted)
                            next = IDLE;
                        else
                            next = STAT;
            default :       next = XXX;
        endcase
    end

    //
    // Interrupt
    //
    assign Irq = irq_status & irq_enable;

    //
    // Error Status Code
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)       err_stat    <= '0;
        else case(state)
            IDLE    :   if (go_pulse && (length == '0))
                            err_stat    <= 2'b10;
                        else
                            err_stat    <= '0;
        endcase

    //
    // Busy Signal (to RegFile)
    // (We can make it un-registered to avoid the 1-cycle update if that becomes
    //  an issue)
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)
            busy    <= '0;
        else
            busy    <= (state != IDLE);

    //
    // IRQ Status (to RegFile)
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)
                irq_status  <= '0;
        else begin
            if ((state == STAT) && read_stat_posted && write_stat_posted)
                irq_status  <= '1;
            else if (irq_clear_pulse)
                irq_status  <= '0;
        end

    //
    // Status Code (to RegFile)
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)       stat_code   <= '0;
        else case (state)
            STAT    :   if (read_stat_posted & write_stat_posted) begin
                            if (err_stat != '0)
                                stat_code   <= err_stat;
                            else if (read_stat != '0)
                                stat_code   <= read_stat;
                            else
                                stat_code   <= write_stat;
                        end
        endcase

    //
    // Read Status
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            read_stat_posted    <= '0;
            read_stat           <= '0;
        end
        else case (state)
            IDLE    :   begin
                            read_stat_posted    <= '0;
                            read_stat           <= '0;
                        end
            STAT    :   if (rd_trans_stat_intf.Valid || (err_stat != '0)) begin
                            read_stat_posted    <= '1;
                            read_stat           <= rd_trans_stat_intf.Data;
                        end
        endcase

    assign rd_trans_stat_intf.Ready = state == STAT;

    //
    // Write Status
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            write_stat_posted    <= '0;
            write_stat           <= '0;
        end
        else case (state)
            IDLE    :   begin
                            write_stat_posted    <= '0;
                            write_stat           <= '0;
                        end
            STAT    :   if (wr_trans_stat_intf.Valid || (err_stat != '0)) begin
                            write_stat_posted    <= '1;
                            write_stat           <= wr_trans_stat_intf.Data;
                        end
        endcase

    assign wr_trans_stat_intf.Ready = state == STAT;

    //
    // Read Command
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
            read_cmd_posted             <= '0;
            rd_trans_cmd_intf.Valid     <= '0;
            rd_trans_cmd_intf.Data      <= '0;
        end
        else case (state)
            IDLE    :   begin
                            read_cmd_posted <= '0;
                            if (go_pulse && (length != '0)) begin
                                rd_trans_cmd_intf.Valid         <= '1;
                                rd_trans_cmd_intf.Data.NumBytes <= length;
                                rd_trans_cmd_intf.Data.Address  <= src_addr;
                            end
                            else begin
                                rd_trans_cmd_intf.Valid         <= '0;
                                rd_trans_cmd_intf.Data          <= '0;
                            end
                        end
            POST    :   if (rd_trans_cmd_intf.Ready) begin
                            read_cmd_posted                     <= '1;
                            rd_trans_cmd_intf.Valid             <= '0;
                        end
        endcase

    //
    // Write Command
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
            write_cmd_posted            <= '0;
            wr_trans_cmd_intf.Valid     <= '0;
            wr_trans_cmd_intf.Data      <= '0;
        end
        else case (state)
            IDLE    :   begin
                            write_cmd_posted    <= '0;
                            if (go_pulse && (length != '0)) begin
                                wr_trans_cmd_intf.Valid         <= '1;
                                wr_trans_cmd_intf.Data.NumBytes <= length;
                                wr_trans_cmd_intf.Data.Address  <= dst_addr;
                            end
                            else begin
                                wr_trans_cmd_intf.Valid         <= '0;
                                wr_trans_cmd_intf.Data          <= '0;
                            end
                        end
            POST    :   if (wr_trans_cmd_intf.Ready) begin
                            write_cmd_posted                    <= '1;
                            wr_trans_cmd_intf.Valid             <= '0;
                        end
        endcase

endmodule
