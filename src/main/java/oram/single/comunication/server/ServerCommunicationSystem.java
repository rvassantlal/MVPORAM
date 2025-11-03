package oram.single.comunication.server;

import oram.single.comunication.client.ClientCommunicationSystem;
import oram.single.comunication.util.BasicChannelInitializer;
import oram.single.comunication.util.SSLChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Future;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Collections;

public class ServerCommunicationSystem extends ClientCommunicationSystem {
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel serverChannel;
	private static final int connectionBacklog = 1024;
	private static final int bossThreads = 1;

	public ServerCommunicationSystem(int myId, String myIpAddress, int listeningPort,
									 int workerNThreads, int maxMessageSize) throws InterruptedException, IOException,
			CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
		super(myId, workerNThreads, maxMessageSize);
		startServer(myIpAddress, listeningPort, workerNThreads, maxMessageSize);
	}

	private void startServer(String myIpAddress, int listeningPort, int workerNThreads,
							 int maxMessageSize) throws InterruptedException, CertificateException, IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
		bossGroup = new NioEventLoopGroup(bossThreads);
		//Runtime.getRuntime().availableProcessors()
		workerGroup = new NioEventLoopGroup(workerNThreads);

		ChannelInitializer<Channel> channelInitializer = new BasicChannelInitializer(this,
				maxMessageSize, this);

		ServerBootstrap serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_REUSEADDR, true)
				//.option(ChannelOption.SO_KEEPALIVE, true)
				//.option(ChannelOption.TCP_NODELAY, true)
				//.option(ChannelOption.SO_SNDBUF, tcpSendBufferSize)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMsec)
				.option(ChannelOption.SO_BACKLOG, connectionBacklog)
				.childHandler(channelInitializer)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.TCP_NODELAY, true);
		ChannelFuture f = serverBootstrap.bind(new InetSocketAddress(myIpAddress, listeningPort)).sync();
		f.addListener(future -> {
			if (future.isSuccess()) {
				logger.info("Server listening on " + myIpAddress + ":" + listeningPort);
			} else {
				logger.error("Server failed to bind to " + myIpAddress + ":" + listeningPort);
			}
		});
		serverChannel = f.channel();
	}

	private ChannelInitializer<Channel> createChannelInitializer(int maxMessageSize) throws NoSuchAlgorithmException,
			KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		String sslTLSKeyStore = "EC_KeyPair_256.pkcs12";

		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		try (FileInputStream fis = new FileInputStream("config/keysSSL_TLS/" + sslTLSKeyStore)) {
			ks.load(fis, SECRET.toCharArray());
		}

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
		keyManagerFactory.init(ks, SECRET.toCharArray());

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
		trustManagerFactory.init(ks);

		Iterable<String> cipher = Collections.singletonList("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");

		SslContext sslContext = SslContextBuilder.forServer(keyManagerFactory)
				//.sslProvider(SslProvider.OPENSSL)
				.trustManager(trustManagerFactory)
				.ciphers(cipher)
				.build();
		return new SSLChannelInitializer(sslContext, this, maxMessageSize, this);
	}

	@Override
	public void shutdown() {
		super.shutdown();
		//shutdownChannel(serverChannel);
		Future<?> bossShutdownFuture = bossGroup.shutdownGracefully();
		Future<?> workerShutdownFuture = workerGroup.shutdownGracefully();
		bossShutdownFuture.syncUninterruptibly();
		workerShutdownFuture.syncUninterruptibly();
	}
}
