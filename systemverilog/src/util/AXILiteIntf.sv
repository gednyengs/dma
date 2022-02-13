// =============================================================================
// filename     : AxiLiteIntf.sv
// description  : AXI4 Lite Interface
// author       : Gedeon Nyengele
// =============================================================================
interface AXILiteIntf;

    // AW Channel
    logic           AWREADY;
    logic           AWVALID;
    logic [31:0]    AWADDR;
    logic [2:0]     AWPROT;

    // W Channel
    logic           WREADY;
    logic           WVALID;
    logic [31:0]    WDATA;
    logic [3:0]     WSTRB;

    // B Channel
    logic           BREADY;
    logic           BVALID;
    logic [1:0]     BRESP;

    // AR Channel
    logic           ARREADY;
    logic           ARVALID;
    logic [31:0]    ARADDR;
    logic [2:0]     ARPROT;

    // R Channel
    logic           RREADY;
    logic           RVALID;
    logic [31:0]    RDATA;
    logic [1:0]     RRESP;

    //
    // Master View
    //
    modport Master (
        // AW Channel
        input       AWREADY,
        output      AWVALID,
        output      AWADDR, AWPROT,

        // W Channel
        input       WREADY,
        output      WVALID,
        output      WDATA, WSTRB,

        // B Channel
        output      BREADY,
        input       BVALID,
        input       BRESP,

        // AR Channel
        input       ARREADY,
        output      ARVALID,
        output      ARADDR, ARPROT,

        // R Channel
        output      RREADY,
        input       RVALID,
        input       RDATA, RRESP
    );

    //
    // Slave View
    //
    modport Slave (
        // AW Channel
        output      AWREADY,
        input       AWVALID,
        input       AWADDR, AWPROT,

        // W Channel
        output      WREADY,
        input       WVALID,
        input       WDATA, WSTRB,

        // B Channel
        input       BREADY,
        output      BVALID,
        output      BRESP,

        // AR Channel
        output      ARREADY,
        input       ARVALID,
        input       ARADDR, ARPROT,

        // R Channel
        input       RREADY,
        output      RVALID,
        output      RDATA, RRESP
    );

endinterface
