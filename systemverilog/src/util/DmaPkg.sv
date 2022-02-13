package DmaPkg;

    //
    // Read/Write Engine Command Format
    //
    typedef struct packed {
        logic   [11:0]  NumBytes;
        logic   [31:0]  Address;
    } Cmd_t;

    //
    // Transfer Command Format
    // (A transaction is broken into transfers)
    //
    typedef struct packed {
        logic   [11:0]  NumBytes;
        logic   [31:0]  SrcAddr;
        logic   [31:0]  DestAddr;
    } XferCmd_t;

    //
    // Transaction Command Format
    // (A transaction is specified by the user)
    //
    typedef struct packed {
        logic   [31:0]  NumBytes;
        logic   [31:0]  Address;
    } TransCmd_t;

    //
    // Reader-Queue-Writer Packet type
    //
    typedef logic [63:0]    Packet_t;

endpackage
