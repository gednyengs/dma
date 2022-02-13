package AxiPkg;

    //
    // Write Address Channel
    //
    typedef struct packed {
        logic   [1:0]           AWID;
        logic   [31:0]          AWADDR;
        logic   [7:0]           AWLEN;
        logic   [2:0]           AWSIZE;
        logic   [1:0]           AWBURST;
        logic                   AWLOCK;
        logic   [3:0]           AWCACHE;
        logic   [2:0]           AWPROT;
    } AxiWrAddr_t;

    //
    // Write Data Channel
    //
    typedef struct packed {
        logic   [63:0]          WDATA;
        logic   [7:0]           WSTRB;
        logic                   WLAST;
    } AxiWrData_t;

    //
    // Write Response Channel
    //
    typedef struct packed {
        logic   [1:0]           BID;
        logic   [1:0]           BRESP;
    } AxiWrResp_t;

    //
    // Read Address Channel
    //
    typedef struct packed {
        logic   [1:0]           ARID;
        logic   [31:0]          ARADDR;
        logic   [7:0]           ARLEN;
        logic   [2:0]           ARSIZE;
        logic   [1:0]           ARBURST;
        logic                   ARLOCK;
        logic   [3:0]           ARCACHE;
        logic   [2:0]           ARPROT;
    } AxiRdAddr_t;

    //
    // Read Data Channel
    //
    typedef struct packed {
        logic   [1:0]           RID;
        logic   [63:0]          RDATA;
        logic   [1:0]           RRESP;
        logic                   RLAST;
    } AxiRdData_t;

endpackage
