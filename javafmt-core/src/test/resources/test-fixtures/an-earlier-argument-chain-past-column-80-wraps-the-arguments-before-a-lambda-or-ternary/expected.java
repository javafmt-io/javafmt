class Calls {

    void lambdaAfterAChainPastColumn80() {
        executorWithAVeryLongNameThatPushesTheCallOut.submit(
                receiverName.first().second().third(), x -> service.process(x));
    }

    void ternaryAfterAChainPastColumn80() {
        executorWithAVeryLongNameThatPushesTheCallOut.submit(
                receiverName.first().second().third(),
                flag
                        ? service.process(x)
                        : other);
    }

    void lambdaAfterAChainWithinColumn80() {
        executor.submit(receiver.first().second().third(), x -> service.processTheRequestWithAVeryLongMethodName(
                x.tenantId(), x.spaceId(), x.payload(), x.receivedAt()));
    }
}
