// =============================================================================
// filename     : Writer.sv
// description  : AXI4 Write Engine
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

module Writer (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // Reader Cmd/Stat Interface
    ReadyValidIntf.Slave    CmdIntf,
    ReadyValidIntf.Master   StatIntf,

    // Data Interface
    ReadyValidIntf.Slave    DataIntf,

    // AXI4 Write Interface
    AXI4WriteIntf.Master    WriteIntf
);

    typedef enum logic [2:0] {
        IDLE        = 3'b000,
        ADDR        = 3'b001,
        DATA        = 3'b011,
        RESP        = 3'b010,
        STAT        = 3'b110,
        XXX         = 'x
    } state_e;

    state_e         state, next;
    logic [1:0]     write_resp;
    logic [11:0]    num_bytes;
    logic [7:0]     aw_len;
    logic           last_beat;

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
            IDLE    :   if (CmdIntf.Valid)             // CmdIntf.Ready should be TRUE
                            next = ADDR;
                        else
                            next = IDLE; //@ loopback
            ADDR    :   if (WriteIntf.WrAddrReady)     // WriteIntf.WrAddrValid should be TRUE
                            next = DATA;
                        else
                            next = ADDR; //@ loopback
            DATA    :   if (last_beat)
                            next = RESP;
                        else
                            next = DATA; //@ loopback
            RESP    :   if (WriteIntf.WrRespValid)          // WriteIntf.WrRespReady should be TRUE
                            next = STAT;
                        else
                            next = RESP; //@ loopback
            STAT    :   if (StatIntf.Ready)                 // StatIntf.Valid should be TRUE
                            next = IDLE;
                        else
                            next = STAT;
            default :       next = XXX;
        endcase
    end

    //
    // AW Channel Signals
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            WriteIntf.WrAddrValid       <= '0;
            WriteIntf.WrAddrPayload     <= '0;
        end
        else begin
            case (state)
                IDLE    :   if (CmdIntf.Valid) begin
                                WriteIntf.WrAddrValid           <= '1;
                                WriteIntf.WrAddrPayload.AWID    <= '0;
                                WriteIntf.WrAddrPayload.AWADDR  <= CmdIntf.Data.Address;
                                WriteIntf.WrAddrPayload.AWLEN   <= aw_len;
                                WriteIntf.WrAddrPayload.AWSIZE  <= 3'b011;
                                WriteIntf.WrAddrPayload.AWBURST <= 2'b01;
                                WriteIntf.WrAddrPayload.AWLOCK  <= 1'b0;
                                WriteIntf.WrAddrPayload.AWCACHE <= 4'b0010;
                                WriteIntf.WrAddrPayload.AWPROT  <= 3'b010;
                            end
                            else WriteIntf.WrAddrValid          <= '0;
                ADDR    :   if (WriteIntf.WrAddrReady)
                                WriteIntf.WrAddrValid           <= '0;
            endcase
        end

    // Status Capture from Write Response
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)               write_resp  <= '0;
        else begin
            case (state)
                IDLE       :        write_resp  <= '0;
                RESP       :    if (WriteIntf.WrRespValid)
                                    write_resp  <= WriteIntf.WrRespPayload.BRESP;
            endcase
        end

    // WDATA
    always_comb begin
        case (state)
            DATA    : WriteIntf.WrDataPayload.WDATA = DataIntf.Data;
            default : WriteIntf.WrDataPayload.WDATA = '0;
        endcase
    end

    // num_bytes
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)       num_bytes   <= '0;
        else case (state)
            IDLE    :   if (CmdIntf.Valid)
                            num_bytes   <= CmdIntf.Data.NumBytes;
                        else
                            num_bytes   <= '0;
            DATA    :   if (WriteIntf.WrDataValid & WriteIntf.WrDataReady)
                            if (num_bytes > 12'h8) num_bytes <= num_bytes - 12'h8;
            RESP    :   num_bytes <= '0;
        endcase

    // WSTRB
    always_comb begin
        case (state)
            DATA    :   if (num_bytes >= 12'h8)
                            WriteIntf.WrDataPayload.WSTRB = '1;
                        else case (num_bytes[2:0])
                            3'b001 : WriteIntf.WrDataPayload.WSTRB = 8'h01;
                            3'b010 : WriteIntf.WrDataPayload.WSTRB = 8'h03;
                            3'b011 : WriteIntf.WrDataPayload.WSTRB = 8'h07;
                            3'b100 : WriteIntf.WrDataPayload.WSTRB = 8'h0F;
                            3'b101 : WriteIntf.WrDataPayload.WSTRB = 8'h1F;
                            3'b110 : WriteIntf.WrDataPayload.WSTRB = 8'h3F;
                            3'b111 : WriteIntf.WrDataPayload.WSTRB = 8'h7F;
                            default: WriteIntf.WrDataPayload.WSTRB = 8'h00;
                        endcase
            default :       WriteIntf.WrDataPayload.WSTRB = '0;
        endcase
    end

    // WVALID
    always_comb begin
        case(state)
            DATA    :   WriteIntf.WrDataValid = DataIntf.Valid;
            default :   WriteIntf.WrDataValid = '0;
        endcase
    end

    // WLAST
    always_comb begin
        case (state)
            DATA    :   WriteIntf.WrDataPayload.WLAST = num_bytes <= 12'h8;
            default :   WriteIntf.WrDataPayload.WLAST = '0;
        endcase
    end

    // Internal AWLEN Signal
    assign aw_len = ((CmdIntf.Data.NumBytes >> 3) + (| CmdIntf.Data.NumBytes[2:0])) - 1'b1;

    // Internal Last Beat Signal
    assign last_beat =  WriteIntf.WrDataValid & WriteIntf.WrDataReady &
                        WriteIntf.WrDataPayload.WLAST;

    // Write Response Ready Signal
    assign WriteIntf.WrRespReady = state == RESP;

    // Command Status Signals
    assign StatIntf.Valid   = state == STAT;
    assign StatIntf.Data    = write_resp;

    // Command Interface Signals
    assign CmdIntf.Ready    = state == IDLE;

    // Data Interface `Ready` Signal
    assign DataIntf.Ready   = (state == DATA) && WriteIntf.WrDataReady;
endmodule
