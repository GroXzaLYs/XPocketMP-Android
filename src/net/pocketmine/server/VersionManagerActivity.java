package net.pocketmine.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLConnection;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;

import com.actionbarsherlock.app.SherlockActivity;

public class VersionManagerActivity extends SherlockActivity {

    private Boolean install = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        install = getIntent().getBooleanExtra("install", false);
        setContentView(R.layout.version_manager);
        start();
    }

    public String getPageContext(String url) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
        StringBuilder sb = new StringBuilder();
        String str;
        while ((str = in.readLine()) != null) {
            sb.append(str);
        }
        in.close();
        return sb.toString();
    }

    private void start() {
        final ProgressBar pbar = findViewById(R.id.loadingBar);
        final ScrollView scrollView = findViewById(R.id.scrollView);
        final Button skip = findViewById(R.id.skipBtn);

        skip.setOnClickListener(v -> finish());
        pbar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
        skip.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Ambil daftar release dari Poggit (GitHub)
                JSONArray releases = (JSONArray) JSONValue.parse(
                        getPageContext("https://api.github.com/repos/pmmp/PocketMine-MP/releases"));

                if (releases == null || releases.isEmpty()) {
                    throw new Exception("No releases found.");
                }

                // Stable (release pertama)
                JSONObject stableObj = (JSONObject) releases.get(0);
                final String stableVersion = (String) stableObj.get("tag_name");
                final String stableDate = ((String) stableObj.get("published_at")).substring(0, 10);
                JSONArray stableAssets = (JSONArray) stableObj.get("assets");
                JSONObject stableAsset = stableAssets.isEmpty() ? null : (JSONObject) stableAssets.get(0);
                final String stableDownloadURL = stableAsset != null
                        ? (String) stableAsset.get("browser_download_url")
                        : "";

                // Beta (prerelease)
                JSONObject betaObj = null;
                for (Object o : releases) {
                    JSONObject rel = (JSONObject) o;
                    Boolean prerelease = (Boolean) rel.get("prerelease");
                    if (prerelease != null && prerelease) {
                        betaObj = rel;
                        break;
                    }
                }

                final String betaVersion = betaObj != null ? (String) betaObj.get("tag_name") : "No beta";
                final String betaDate = betaObj != null
                        ? ((String) betaObj.get("published_at")).substring(0, 10)
                        : "-";
                JSONArray betaAssets = betaObj != null ? (JSONArray) betaObj.get("assets") : new JSONArray();
                JSONObject betaAsset = betaAssets.isEmpty() ? null : (JSONObject) betaAssets.get(0);
                final String betaDownloadURL = betaAsset != null
                        ? (String) betaAsset.get("browser_download_url")
                        : "";

                runOnUiThread(() -> {
                    TextView stableVersionView = findViewById(R.id.stable_version);
                    TextView stableDateView = findViewById(R.id.stable_date);
                    Button stableDownload = findViewById(R.id.download_stable);

                    TextView betaVersionView = findViewById(R.id.beta_version);
                    TextView betaDateView = findViewById(R.id.beta_date);
                    Button betaDownload = findViewById(R.id.download_beta);

                    stableVersionView.setText("Version: " + stableVersion);
                    stableDateView.setText(stableDate);
                    stableDownload.setOnClickListener(v -> download(stableDownloadURL, stableVersion));

                    betaVersionView.setText("Version: " + betaVersion);
                    betaDateView.setText(betaDate);
                    if (!betaDownloadURL.isEmpty()) {
                        betaDownload.setOnClickListener(v -> download(betaDownloadURL, betaVersion));
                    } else {
                        betaDownload.setEnabled(false);
                        betaDownload.setText("No Beta");
                    }

                    pbar.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);
                    if (install) {
                        skip.setVisibility(ServerUtils.checkIfInstalled() ? View.VISIBLE : View.GONE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "Gagal memuat versi dari Poggit.", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void download(final String address, final String fver) {
        File vdir = new File(ServerUtils.getDataDirectory() + "/versions/");
        if (!vdir.exists()) vdir.mkdirs();

        final VersionManagerActivity ctx = this;
        runOnUiThread(() -> {
            final ProgressDialog dlDialog = new ProgressDialog(ctx);
            dlDialog.setMax(100);
            dlDialog.setTitle("Downloading...");
            dlDialog.setMessage("Please wait...");
            dlDialog.setIndeterminate(false);
            dlDialog.setCancelable(false);
            dlDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dlDialog.show();
            dlDialog.setProgress(0);

            new Thread(() -> {
                try {
                    URL url = new URL(address);
                    URLConnection connection = url.openConnection();
                    connection.connect();
                    int fileLength = connection.getContentLength();

                    InputStream input = new BufferedInputStream(url.openStream());
                    OutputStream output = new FileOutputStream(ServerUtils.getDataDirectory()
                            + "/versions/" + fver + ".phar");

                    byte data[] = new byte[1024];
                    long total = 0;
                    int count;
                    int lastProgress = 0;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        int progress = (int) (total * 100 / fileLength);
                        if (progress != lastProgress) {
                            dlDialog.setProgress(progress);
                            lastProgress = progress;
                        }
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    dlDialog.dismiss();
                    install(fver);
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                            "Gagal mengunduh versi ini.", Toast.LENGTH_SHORT).show());
                    dlDialog.dismiss();
                }
            }).start();
        });
    }

    private void install(CharSequence ver) {
        final VersionManagerActivity ctx = this;
        final CharSequence fver = ver;

        runOnUiThread(() -> {
            final ProgressDialog iDialog = new ProgressDialog(ctx);
            iDialog.setMax(100);
            iDialog.setTitle("Installing...");
            iDialog.setMessage("Please wait...");
            iDialog.setIndeterminate(false);
            iDialog.setCancelable(false);
            iDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            iDialog.show();

            new Thread(() -> {
                try {
                    new File(ServerUtils.getDataDirectory() + "/PocketMine-MP.phar").delete();

                    FileInputStream in = new FileInputStream(ServerUtils.getDataDirectory()
                            + "/versions/" + fver + ".phar");
                    FileOutputStream out = new FileOutputStream(ServerUtils.getDataDirectory()
                            + "/PocketMine-MP.phar");
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    out.close();

                    runOnUiThread(() -> {
                        if (install) {
                            Intent verIntent = new Intent(ctx, ConfigActivity.class);
                            verIntent.putExtra("install", true);
                            startActivity(verIntent);
                        }
                        ctx.finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(getApplicationContext(),
                                    "Gagal menginstal versi ini.", Toast.LENGTH_SHORT).show());
                }
                iDialog.dismiss();
            }).start();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && install) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
