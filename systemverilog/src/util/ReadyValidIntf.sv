interface ReadyValidIntf #(type DataTy);

    logic       Ready;
    logic       Valid;
    DataTy      Data;

    //
    // Master View ( Initiator )
    //
    modport Master (
        input   Ready,
        output  Valid,
        output  Data
    );

    //
    // Slave View
    //
    modport Slave (
        output  Ready,
        input   Valid,
        input   Data
    );

endinterface
