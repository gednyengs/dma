// =============================================================================
// filename     : RegFile.sv
// description  : Controller Register File
// author       : Gedeon Nyengele
// =============================================================================

module RegFile (
    // Clock and Reset
    input   logic           ACLK,
    input   logic           ARESETn,

    // AXI Lite Interface
    AXILiteIntf.Slave       RegIntf,

    // Control Signals
    output  logic           GO_Pulse,
    output  logic           IRQEnable,
    output  logic           IRQClear_Pulse,

    output  logic [31:0]    SrcAddr,
    output  logic [31:0]    DstAddr,
    output  logic [31:0]    Length,

    // Status Signals
    input   logic           Busy,
    input   logic           IRQStatus,
    input   logic [1:0]     StatCode
);

    // Registers
    logic           reg_go_pulse;       // GO Pulse
    logic           reg_ie;             // Interrupt Enable
    logic           reg_intclr_pulse;   // Interrupt Clear Pulse
    logic [31:0]    reg_src_addr;       // Source Address
    logic [31:0]    reg_dst_addr;       // Destination Address
    logic [31:0]    reg_length;         // Length

    // Internal Signals (Implemented like Xilinx AXI4 Peripheral)
    logic [31:0]    axi_awaddr;
    logic           axi_awready;
    logic           axi_wready;
    logic [1:0]     axi_bresp;
    logic           axi_bvalid;
    logic [31:0]    axi_araddr;
    logic           axi_arready;
    logic [31:0]    axi_rdata;
    logic [1:0]     axi_rresp;
    logic           axi_rvalid;

    logic           slv_reg_rden;
    logic           slv_reg_wren;
    logic           aw_en;
    logic [31:0]    reg_data_out;
    integer         idx;

    //
    // AXI Lite I/O Connections
    //
    assign RegIntf.AWREADY      = axi_awready;
    assign RegIntf.WREADY       = axi_wready;
    assign RegIntf.BRESP        = axi_bresp;
    assign RegIntf.BVALID       = axi_bvalid;
    assign RegIntf.ARREADY      = axi_arready;
    assign RegIntf.RDATA        = axi_rdata;
    assign RegIntf.RRESP        = axi_rresp;
    assign RegIntf.RVALID       = axi_rvalid;

    //
    // Implement `axi_awready`
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            axi_awready     <= '0;
            aw_en           <= '1;
        end
        else begin
            if (~axi_awready && RegIntf.AWVALID && RegIntf.WVALID && aw_en) begin
                axi_awready <= '1;
                aw_en       <= '0;
            end
            else if (RegIntf.BREADY && axi_bvalid) begin
                axi_awready <= '0;
                aw_en       <= '1;
            end
            else begin
                axi_awready <= '0;
            end
        end

    //
    // Register `axi_awaddr`
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)
            axi_awaddr    <= '0;
        else begin
            if (~axi_awready && RegIntf.AWVALID && RegIntf.WVALID && aw_en)
                axi_awaddr  <= RegIntf.AWADDR;
        end

    //
    // Implement `axi_wready`
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)
            axi_wready  <= '0;
        else begin
            if (~axi_wready && RegIntf.WVALID && RegIntf.AWVALID && aw_en)
                axi_wready  <= '1;
            else
                axi_wready  <= '0;
        end

    //
    // Write Response Logic
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
            axi_bvalid  <= '0;
            axi_bresp   <= '0;
        end
        else begin
            if (axi_awready && RegIntf.AWVALID && ~axi_bvalid && axi_wready && RegIntf.WVALID)
            begin
                axi_bvalid  <= '1;
                axi_bresp   <= 2'h0;
            end
            else if (RegIntf.BREADY & axi_bvalid)
                axi_bvalid  <= '0;
        end
    //
    // Write Enable Signal
    //
    assign slv_reg_wren = axi_awready && RegIntf.AWVALID && axi_wready && RegIntf.WVALID;

    //
    // Generate GO Pulse
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   reg_go_pulse    <= '0;
        else begin
            reg_go_pulse    <= '0;
            if (slv_reg_wren) begin
                case (axi_awaddr[4:2])
                    3'h0    :   if (RegIntf.WSTRB[0])
                                    reg_go_pulse    <= RegIntf.WDATA[0];
                endcase
            end
        end

    assign GO_Pulse = reg_go_pulse;

    //
    // Generate Interrupt Clear Pulse
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   reg_intclr_pulse    <= '0;
        else begin
            reg_intclr_pulse    <= '0;
            if (slv_reg_wren) begin
                case (axi_awaddr[4:2])
                    3'h2    :   if (RegIntf.WSTRB[0])
                                    reg_intclr_pulse    <= RegIntf.WDATA[1];
                endcase
            end
        end

    assign IRQClear_Pulse = reg_intclr_pulse;

    //
    // Interrupt Enable Register
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   reg_ie  <= '0;
        else begin
            if (slv_reg_wren) begin
                case (axi_awaddr[4:2])
                    3'h0    :   if (RegIntf.WSTRB[0])
                                    reg_ie  <= RegIntf.WDATA[1];
                endcase
            end
        end

    assign IRQEnable = reg_ie;

    //
    // Source Address, Destination Address, and Length Registers
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
            reg_src_addr    <= '0;
            reg_dst_addr    <= '0;
            reg_length      <= '0;
        end
        else begin
            if (slv_reg_wren) begin
                case (axi_awaddr[4:2])
                    3'h3    :   for (idx = 0; idx <= 3; idx = idx + 1)
                                    if (RegIntf.WSTRB[idx] == '1) begin
                                        reg_src_addr[(idx*8) +: 8] <= RegIntf.WDATA[(idx*8) +: 8];
                                    end
                    3'h4    :   for (idx = 0; idx <= 3; idx = idx + 1)
                                    if (RegIntf.WSTRB[idx] == '1) begin
                                        reg_dst_addr[(idx*8) +: 8] <= RegIntf.WDATA[(idx*8) +: 8];
                                    end
                    3'h5    :   for (idx = 0; idx <= 3; idx = idx + 1)
                                    if (RegIntf.WSTRB[idx] == '1) begin
                                        reg_length[(idx*8) +: 8] <= RegIntf.WDATA[(idx*8) +: 8];
                                    end
                endcase
            end
        end

    assign SrcAddr  =   reg_src_addr;
    assign DstAddr  =   reg_dst_addr;
    assign Length   =   reg_length;

    //
    // Generate `axi_arready` and Register ARADDR
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn) begin
            axi_arready <= '0;
            axi_araddr  <= '0;
        end
        else begin
            if (~axi_arready && RegIntf.ARVALID) begin
                axi_arready <= '1;
                axi_araddr  <= RegIntf.ARADDR;
            end
            else
                axi_arready <= '0;
        end

    //
    // Generate `axi_rvalid`
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   begin
            axi_rvalid  <= '0;
            axi_rresp   <= '0;
        end
        else begin
            if (axi_arready && RegIntf.ARVALID && ~axi_rvalid) begin
                axi_rvalid  <= '1;
                axi_rresp   <= 2'h0;
            end
            else if (axi_rvalid && RegIntf.RREADY)
                axi_rvalid  <= '0;
        end

    //
    // Read Enable Signal
    //
    assign slv_reg_rden = axi_arready & RegIntf.ARVALID & ~axi_rvalid;

    //
    // Output Select Logic
    //
    always_comb begin
        case (axi_araddr[4:2])
            3'h0    :   reg_data_out = {{30{1'b0}}, reg_ie, 1'b0};
            3'h1    :   reg_data_out = {{28{1'b0}}, StatCode, IRQStatus, Busy};
            3'h2    :   reg_data_out = '0;
            3'h3    :   reg_data_out = reg_src_addr;
            3'h4    :   reg_data_out = reg_dst_addr;
            3'h5    :   reg_data_out = reg_length;
            3'h6    :   reg_data_out =  32'h5A5A5A5A;
            default :   reg_data_out = '0;
        endcase
    end

    //
    // Output Register
    //
    always_ff @(posedge ACLK or negedge ARESETn)
        if (!ARESETn)   axi_rdata   <=- '0;
        else begin
            if (slv_reg_rden)
                axi_rdata   <= reg_data_out;
        end
endmodule
