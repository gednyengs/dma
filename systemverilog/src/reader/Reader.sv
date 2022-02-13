// =============================================================================
// filename     : Reader.sv
// description  : AXI4 Read Engine
// author       : Gedeon Nyengele
// =============================================================================
//
// Assumptions:
//  - Transfer address is 8-byte-aligned
//  - Transfer address + number of bytes does not cross 2KB boundary
//
// TO-DO:
//  - Add support for ABORT
//
// =============================================================================
module Reader (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Reader Cmd/Stat Interface
    ReadyValidIntf.Slave    CmdIntf,
    ReadyValidIntf.Master   StatIntf,

    // Data Interface
    ReadyValidIntf.Master   DataIntf,

    // AXI4 Read Interface
    AXI4ReadIntf.Master     ReadIntf
);

    typedef enum logic [1:0] {
        IDLE    = 2'b00,
        ADDR    = 2'b01,
        DATA    = 2'b11,
        STAT    = 2'b10,
        XXX     = 'x
    } state_e;

    state_e         state, next;
    logic           last_beat;
    logic [1:0]     read_resp;
    logic [7:0]     ar_len;

    //
    // State Register Update Logic
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
            IDLE    :   if (CmdIntf.Valid)
                            next = ADDR;
                        else
                            next = IDLE; //@ loopback
            ADDR    :   if (ReadIntf.RdAddrReady)
                            next = DATA;
                        else
                            next = ADDR; //@ loopback
            DATA    :   if (last_beat)
                            next = STAT;
                        else
                            next = DATA; //@ loopback
            STAT   :   if (StatIntf.Ready)
                            next = IDLE;
                        else
                            next = STAT; //@ loopback
            default :       next = XXX;
        endcase // state
    end // next state logic

    //
    // AR Channel Control Signals
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            ReadIntf.RdAddrValid    <= '0;
            ReadIntf.RdAddrPayload  <= '0;
        end
        else begin
            case (state)
                IDLE    :   if (CmdIntf.Valid) begin
                                ReadIntf.RdAddrValid            <= '1;
                                ReadIntf.RdAddrPayload.ARID     <= '0;
                                ReadIntf.RdAddrPayload.ARADDR   <= CmdIntf.Data.Address;
                                ReadIntf.RdAddrPayload.ARLEN    <= ar_len;
                                ReadIntf.RdAddrPayload.ARSIZE   <= 3'b011;
                                ReadIntf.RdAddrPayload.ARBURST  <= 2'b01;
                                ReadIntf.RdAddrPayload.ARLOCK   <= 1'b0;
                                ReadIntf.RdAddrPayload.ARCACHE  <= 4'b0010;
                                ReadIntf.RdAddrPayload.ARPROT   <= 3'b010;
                            end
                            else ReadIntf.RdAddrValid           <= '0;
                ADDR    :   if (ReadIntf.RdAddrReady)
                                ReadIntf.RdAddrValid            <= '0;
            endcase
        end

    // Internal read response error accumulation.
    // We make read errors sticky
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   read_resp <= '0;
        else
            case (state)
                IDLE    :       read_resp <= '0;
                DATA    :   if (ReadIntf.RdDataValid & ReadIntf.RdDataReady)
                                read_resp <= (read_resp == '0) ?
                                             ReadIntf.RdDataPayload.RRESP : read_resp;
            endcase


    // RREADY
    always_comb begin
        case (state)
            DATA    :   ReadIntf.RdDataReady = DataIntf.Ready;
            default :   ReadIntf.RdDataReady = '0;
        endcase
    end

    // Internal signals
    assign last_beat = ReadIntf.RdDataValid & ReadIntf.RdDataReady & ReadIntf.RdDataPayload.RLAST;
    assign ar_len = ((CmdIntf.Data.NumBytes >> 3) + (| CmdIntf.Data.NumBytes[2:0])) - 1'b1;

    // Status Signals
    assign StatIntf.Valid = state == STAT;
    assign StatIntf.Data  = read_resp;

    //
    // Data Interface Signals
    //
    assign DataIntf.Valid       = (state == DATA) && ReadIntf.RdDataValid;
    assign DataIntf.Data        = ReadIntf.RdDataPayload.RDATA;

    // Command Interface Signals
    assign CmdIntf.Ready        = state == IDLE;

endmodule
