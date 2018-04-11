package com.netty.file.server;

import com.netty.file.config.ServerConfig;
import com.netty.file.handler.FileUploadAdaptorHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * @author guandezhi
 * @ClassName: com.netty.file.server
 * @Description:
 * @date 2018/4/10 15:47
 */
@Component
public class FileStoreServer {

    private static final Logger logger = LoggerFactory.getLogger(FileStoreServer.class);


    private ServerBootstrap serverBootstrap = new ServerBootstrap();

    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    @Resource
    private ServerConfig serverConfig;

    @PostConstruct
    public void start(){
        int port = serverConfig.getPort();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class);


        new Thread(() -> {
            try {
                serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline channelPipeline = socketChannel.pipeline();
                        channelPipeline.addLast("decoder", new HttpRequestDecoder());
                        channelPipeline.addLast("encoder", new HttpResponseEncoder());
                        // 此Handler的作用是自动压缩，如果不想压缩，可以删去
                        channelPipeline.addLast(new HttpContentCompressor());
                        channelPipeline.addLast("handler", new FileUploadAdaptorHandler());
                    }
                });
                logger.info("netty服务器在{}端口启动监听", port);
                serverBootstrap.bind(port).sync().channel().closeFuture().sync();
            } catch (InterruptedException e) {
                logger.info("InterruptedException异常={}", e.getMessage());
                e.printStackTrace();
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();
    }

    /**
     * 关闭服务器方法
     */
    @PreDestroy
    public void close() {
        logger.info("关闭服务器......");
        // 优雅退出
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
