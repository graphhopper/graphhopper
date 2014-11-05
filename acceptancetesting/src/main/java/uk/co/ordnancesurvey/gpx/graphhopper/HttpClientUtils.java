package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

public class HttpClientUtils {
    private static final String SSL_KEY_FILE = "sslKeyFile";
    private static HttpClientBuilder builder = null;

    static {
        createBasicClient();
        try {
            configureOutboundSSL();
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private static void configureOutboundSSL() throws KeyManagementException, NoSuchAlgorithmException {
        KeyManager[] kms = null;
        String keyFileName = IntegrationTestProperties.getTestProperty(SSL_KEY_FILE);
        if (null != keyFileName) {
            File keyFile = new File(keyFileName);
            if (keyFile.canRead()) {
                try {
                    kms = buildKeyManager(keyFile);
                } catch (KeyStoreException | UnrecoverableKeyException | CertificateException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
        SSLContext sslcontext;
        sslcontext = buildContext(kms);
        builder.setSslcontext(sslcontext);
    }

    private static void createBasicClient() {
        builder = HttpClients.custom();
        builder.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    public static CloseableHttpClient createClient() {
        return builder.build();
    }

    /**
     * Builds a valid ssl context using TLS and the the supplied key manager
     * creating a key store but no trust store.
     *
     * @param kms
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private static SSLContext buildContext(KeyManager[] kms) throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] tms = createTrustManager();

        SSLContext sslContext = null;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kms, tms, new SecureRandom());
        return sslContext;
    }

    private static KeyManager[] buildKeyManager(File keyFile) throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            IOException, UnrecoverableKeyException {
        KeyStore clientStore = KeyStore.getInstance("PKCS12");
        try (final FileInputStream keyStream = new FileInputStream(keyFile)) {
            clientStore.load(keyStream, "addressapi".toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientStore, "addressapi".toCharArray());
        KeyManager[] kms = kmf.getKeyManagers();
        return kms;
    }

    /**
     * Creates a permissive trust manager. Empty implementations of
     * checkServerTrusted and checkClientTrusted ensure all sessions are treated
     * as trusted.
     *
     * @return
     */
    private static TrustManager[] createTrustManager() {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

        } };
        return trustAllCerts;
    }

}
