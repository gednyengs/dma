// =============================================================================
// filename     : AXI4WriteIntf.sv
// description  : AXI4 Write Interface
// author       : Gedeon Nyengele
// =============================================================================
interface AXI4WriteIntf;

    import AxiPkg::AxiWrAddr_t;
    import AxiPkg::AxiWrData_t;
    import AxiPkg::AxiWrResp_t;

    // AW Channel
    AxiWrAddr_t             WrAddrPayload;
    logic                   WrAddrValid;
    logic                   WrAddrReady;

    // W Channel
    AxiWrData_t             WrDataPayload;
    logic                   WrDataValid;
    logic                   WrDataReady;

    // B Channel
    AxiWrResp_t             WrRespPayload;
    logic                   WrRespValid;
    logic                   WrRespReady;

    modport Master (
        // AW Channel
        output  WrAddrPayload,
        output  WrAddrValid,
        input   WrAddrReady,

        // W Channel
        output  WrDataPayload,
        output  WrDataValid,
        input   WrDataReady,

        // B Channel
        input   WrRespPayload,
        input   WrRespValid,
        output  WrRespReady
    );

    modport Slave (
        // AW Channel
        input   WrAddrPayload,
        input   WrAddrValid,
        output  WrAddrReady,

        // W Channel
        input   WrDataPayload,
        input   WrDataValid,
        output  WrDataReady,

        // B Channel
        output  WrRespPayload,
        output  WrRespValid,
        input   WrRespReady
    );
endinterface
