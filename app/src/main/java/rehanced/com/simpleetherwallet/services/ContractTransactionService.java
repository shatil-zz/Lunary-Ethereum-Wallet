package rehanced.com.simpleetherwallet.services;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.tx.Contract;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import rehanced.com.simpleetherwallet.AppSettings;
import rehanced.com.simpleetherwallet.R;
import rehanced.com.simpleetherwallet.activities.MainActivity;
import rehanced.com.simpleetherwallet.network.EtherscanAPI;
import rehanced.com.simpleetherwallet.utils.ExchangeCalculator;
import rehanced.com.simpleetherwallet.utils.WalletStorage;

public class ContractTransactionService extends IntentService {

    private NotificationCompat.Builder builder;
    final int mNotificationId = 1756;

    public ContractTransactionService() {
        super("Contract Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        sendNotification();
        try {
            String fromAddress = intent.getStringExtra(BundleKeyUtils.EXTRA_FROM_ADDRESS);
            final String amount = intent.getStringExtra(BundleKeyUtils.EXTRA_AMOUNT);
            final String gas_price = intent.getStringExtra(BundleKeyUtils.EXTRA_GAS_PRICE);
            final String gas_limit = intent.getStringExtra(BundleKeyUtils.EXTRA_GAS_LIMIT);
            final String data = intent.getStringExtra(BundleKeyUtils.EXTRA_DATA);
            String password = intent.getStringExtra(BundleKeyUtils.EXTRA_PASSWORD);
            final boolean isCreateContract = intent.getBooleanExtra(BundleKeyUtils.EXTRA_IS_CREATE_OPERATION, false);

            final Credentials keys = WalletStorage.getInstance(getApplicationContext()).getFullWallet(getApplicationContext(), password, fromAddress);

            EtherscanAPI.getInstance().getNonceForAddress(fromAddress, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("txx","Can't connect to network, retry it later"+e.toString());
                    error("Can't connect to network, retry it later");
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    try {
                        JSONObject o = new JSONObject(response.body().string());
                        BigInteger nonce = new BigInteger(o.getString("result").substring(2), 16);
                        RawTransaction tx = null;
                        if (!isCreateContract) {
                            tx = RawTransaction.createContractTransaction(
                                    nonce,
                                    Contract.GAS_PRICE,
                                    Contract.GAS_LIMIT,
                                    new BigDecimal(amount).multiply(ExchangeCalculator.ONE_ETHER).toBigInteger(),
                                    data
                            );
                        } else {
                        }

                        if (tx != null) {
                            Log.d("txx",
                                    "Nonce: " + tx.getNonce() + "\n" +
                                            "gasPrice: " + tx.getGasPrice() + "\n" +
                                            "gasLimit: " + tx.getGasLimit() + "\n" +
                                            "To: " + tx.getTo() + "\n" +
                                            "Amount: " + tx.getValue() + "\n" +
                                            "Data: " + tx.getData()
                            );

                            byte[] signed = TransactionEncoder.signMessage(tx, AppSettings.getChainId(), keys);

                            forwardTX(signed);
                        } else {
                            Log.d("txx", "Txx is null");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.d("txx", "Txx Error:" + e.toString());
                        error("Can't connect to network, retry it later");
                    }
                }
            });

        } catch (Exception e) {
            Log.d("txx", "Txx Invalid:" + e.toString());
            error("Invalid Wallet Password!");
            e.printStackTrace();
        }
    }

    private void forwardTX(byte[] signed) throws IOException {
        EtherscanAPI.getInstance().forwardTransaction("0x" + Hex.toHexString(signed), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                error("Can't connect to network, retry it later");
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                String received = response.body().string();
                Log.d("deploy respnse:","Body: "+received);
                try {
                    suc(new JSONObject(received).getString("result"));
                } catch (Exception e) {
                    // Advanced error handling. If etherscan returns error message show the shortened version in notification. Else abbort with unknown error
                    try {
                        String errormsg = new JSONObject(received).getJSONObject("error").getString("message");
                        if (errormsg.indexOf(".") > 0)
                            errormsg = errormsg.substring(0, errormsg.indexOf("."));
                        error(errormsg); // f.E Insufficient funds
                    } catch (JSONException e1) {
                        error("Unknown error occured");
                    }
                }
            }
        });
    }

    private void suc(String hash) {
        builder
                .setContentTitle(getString(R.string.notification_transfersuc))
                .setProgress(100, 100, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentText("");

        Intent main = new Intent(this, MainActivity.class);
        main.putExtra("STARTAT", 2);
        main.putExtra("TXHASH", hash);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                main, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        final NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotifyMgr.notify(mNotificationId, builder.build());
    }

    private void error(String err) {
        builder
                .setContentTitle(getString(R.string.notification_createfail))
                .setProgress(100, 100, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentText(err);

        Intent main = new Intent(this, MainActivity.class);
        main.putExtra("STARTAT", 2);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                main, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        final NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotifyMgr.notify(mNotificationId, builder.build());
    }

    private void sendNotification() {
        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0x2d435c)
                .setTicker("Creating contract...")
                .setContentTitle("Creating contract")
                .setContentText(getString(R.string.notification_might_take_a_minute))
                .setOngoing(true)
                .setProgress(0, 0, true);
        final NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotifyMgr.notify(mNotificationId, builder.build());
    }


}
