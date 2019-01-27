import java.util.*;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        int inputId = 0;
        Set<UTXO> seenUtxo = new HashSet<>();

        double sumInput = 0.0, sumOutput = 0.0;
        for (Transaction.Input input : tx.getInputs()) {
            // (1) all outputs claimed by tx are in the current UTXO pool.
            UTXO lastUtxo = getUtxo(input);
            if (!this.utxoPool.contains(lastUtxo)) {
                return false;
            }

            // (2) the signatures on each input of tx are valid.
            Transaction.Output spentOutput = this.utxoPool.getTxOutput(lastUtxo);
            if (!Crypto.verifySignature(spentOutput.address, tx.getRawDataToSign(inputId), input.signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by tx.
            if (seenUtxo.contains(lastUtxo)) {
                return false;
            }
            seenUtxo.add(lastUtxo);

            sumInput += spentOutput.value;

            inputId++;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            // (4) all tx output values are non-negative
            if (output.value < 0) {
                return false;
            }

            sumOutput += output.value;
        }

        // (5)
        if (sumInput < sumOutput - 1e-9) {
            return false;
        }
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // 1. Get all valid transactions.
        List<Transaction> transactionList = new LinkedList<>();
        for (Transaction transaction : possibleTxs) {
            if (isValidTx(transaction)) {
                transactionList.add(transaction);

                // Update UTXO pool.
                for (Transaction.Input input : transaction.getInputs()) {
                    UTXO utxo = getUtxo(input);
                    utxoPool.removeUTXO(utxo);
                }
                for (int outputId = 0; outputId < transaction.getOutputs().size(); outputId++) {
                    UTXO utxo = new UTXO(transaction.getHash(), outputId);
                    utxoPool.addUTXO(utxo, transaction.getOutput(outputId));
                }
            }
        }

        // 2. Convert List<Transaction> to Transaction[]
        Transaction[] transactions = new Transaction[transactionList.size()];
        transactions = transactionList.toArray(transactions);
        return transactions;
    }

    private UTXO getUtxo(Transaction.Input input) {
        return new UTXO(input.prevTxHash, input.outputIndex);
    }

    private UTXOPool utxoPool;
}
