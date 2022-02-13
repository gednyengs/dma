// =============================================================================
// filename     : AXI4ReadIntf.sv
// description  : AXI4 Read Interface
// author       : Gedeon Nyengele
// =============================================================================
interface AXI4ReadIntf;

    import AxiPkg::AxiRdAddr_t;
    import AxiPkg::AxiRdData_t;

    // AR Channel
    AxiRdAddr_t             RdAddrPayload;
    logic                   RdAddrValid;
    logic                   RdAddrReady;

    // R Channel
    AxiRdData_t             RdDataPayload;
    logic                   RdDataValid;
    logic                   RdDataReady;

    modport Master (
        // AR Channel
        output RdAddrPayload,
        output RdAddrValid,
        input  RdAddrReady,

        // R Channel
        input  RdDataPayload,
        input  RdDataValid,
        output RdDataReady
    );

    modport Slave (
        // AR Channel
        input  RdAddrPayload,
        input  RdAddrValid,
        output RdAddrReady,

        // R Channel
        output RdDataPayload,
        output RdDataValid,
        input  RdDataReady
    );

endinterface
