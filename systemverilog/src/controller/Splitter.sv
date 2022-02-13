// =============================================================================
// filename     : Splitter.sv
// description  : Splits a user transfer request into smaller transfers
// author       : Gedeon Nyengele
// =============================================================================
//
// Assumptions:
//  - Transfer address is 8-byte-aligned
//
// TO-DO:
//  - Add support for ABORT
//
// =============================================================================
module Splitter (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Transaction Cmd/Stat Interface
    ReadyValidIntf.Slave    TransCmdIntf,
    ReadyValidIntf.Master   TransStatIntf,

    // Cmd/Stat Interface
    ReadyValidIntf.Master   CmdIntf,
    ReadyValidIntf.Slave    StatIntf
);

    typedef enum logic [1:0] {
        IDLE        = 2'b00,
        T_SPLIT     = 2'b01,
        T_WAIT      = 2'b11,
        STAT        = 2'b10,
        XXX         = 'x
    } state_e;

    localparam  MAX_BYTES = (8 * 256);

    state_e         state, next;
    logic [31:0]    cur_addr;
    logic [31:0]    cur_num_bytes;
    logic [31:0]    rem_bytes;
    logic [31:0]    bBytes;

    //
    // Current State Register
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
            IDLE    :   if (TransCmdIntf.Valid)
                            next = T_SPLIT;
                        else
                            next = IDLE; //@ loopback
            T_SPLIT :   if (CmdIntf.Ready)
                            next = T_WAIT;
                        else
                            next = T_SPLIT; //@ loopback
            T_WAIT  :   if (StatIntf.Valid) begin
                            if (rem_bytes > 0)
                                next = T_SPLIT;
                            else
                                next = STAT;
                        end
                        else
                            next = T_WAIT; //@ loopback
            STAT   :    if (TransStatIntf.Ready)
                            next = IDLE;
                        else
                            next = STAT; //@ loopback
        endcase
    end

    //
    // Transaction Address Splitting
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)       cur_addr    <= '0;
        else case (state)
            IDLE    :   if (TransCmdIntf.Valid)
                            cur_addr    <= TransCmdIntf.Data.Address;
            T_WAIT  :   if (StatIntf.Valid)
                            cur_addr    <= {{cur_addr[31:11] + 1'b1}, 11'h0};
        endcase

    //
    // Transaction NumBytes Splitting
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
                            cur_num_bytes   <= '0;
                            rem_bytes       <= '0;
        end
        else case (state)
            IDLE    :   if (TransCmdIntf.Valid) begin
                            if (bBytes <= TransCmdIntf.Data.NumBytes) begin
                                cur_num_bytes   <= bBytes;
                                rem_bytes       <= TransCmdIntf.Data.NumBytes - bBytes;
                            end
                            else begin
                                cur_num_bytes   <= TransCmdIntf.Data.NumBytes;
                                rem_bytes       <= '0;
                            end
                        end
            T_WAIT  :   if (StatIntf.Valid) begin
                            if (rem_bytes > MAX_BYTES) begin
                                cur_num_bytes   <= MAX_BYTES;
                                rem_bytes       <= rem_bytes - MAX_BYTES;
                            end
                            else begin
                                cur_num_bytes   <= rem_bytes;
                                rem_bytes       <= '0;
                            end
                        end
        endcase

    // Transaction Command Status
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   TransStatIntf.Data      <= '0;
        else case (state)
            IDLE    :   TransStatIntf.Data      <= '0;
            T_WAIT  : if((StatIntf.Valid) && (TransStatIntf.Data == 2'b00))
                        TransStatIntf.Data      <= StatIntf.Data;
        endcase

    //
    // Internal Signals
    //
    assign bBytes = MAX_BYTES - TransCmdIntf.Data.Address[10:0];

    //
    // Transaction Cmd/Status
    //
    assign TransCmdIntf.Ready       = state == IDLE;
    assign TransStatIntf.Valid      = state == STAT;

    //
    // Child Command Status Ready
    //
    assign StatIntf.Ready           =  state == T_WAIT;

    //
    // Child Command Assignments
    //
    assign CmdIntf.Valid            = state == T_SPLIT;
    assign CmdIntf.Data.NumBytes    = cur_num_bytes[11:0];
    assign CmdIntf.Data.Address     = cur_addr;

endmodule
