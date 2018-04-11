package com.netty.file.handler;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

import com.netty.file.config.FileConfig;
import com.netty.file.config.ServerConfig;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.*;
import org.json.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author guandezhi
 * @ClassName: com.netty.file.handler
 * @Description:
 * @date 2018/4/10 15:42
 */
@ChannelHandler.Sharable
public class FileUploadAdaptorHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadAdaptorHandler.class);

    private volatile HttpRequest request;
    private volatile boolean readingChunks;
    private volatile StringBuilder responseContent = new StringBuilder();
    private volatile HttpPostRequestDecoder decoder;
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = null;
    }


    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            logger.info("decoder.cleanFiles()......");
            decoder.cleanFiles();
        }
        logger.info("channelUnregistered......");
    }



    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, HttpObject httpObject) throws Exception {
        logger.info("channelRead0.......");

        if (httpObject instanceof HttpRequest) {
            logger.info("httpObject instanceof HttpRequest......");
            this.request = (HttpRequest) httpObject;
            HttpMethod httpMethod = this.request.method();
            logger.info("method is {}", httpMethod);
            if (HttpMethod.GET.equals(httpMethod)) {
                responseContent.append(new JSONObject().put("code", "404").put("msg", "GET method is not support"));
                writeResponseString(ctx);
                return;
            } else if (HttpMethod.POST.equals(httpMethod)) {
                try {
                    decoder = new HttpPostRequestDecoder(factory, request);
                } catch (ErrorDataDecoderException e) {
                    e.printStackTrace();
                    responseContent.append(e.getMessage());
                    writeResponseString(ctx);
                    ctx.channel().close();
                    return;
                }
            } else {
                responseContent.append(new JSONObject().put("code", "404").put("msg", "method is not support"));
                writeResponseString(ctx);
                return;
            }
        } else if (httpObject instanceof HttpContent) {
            logger.info("httpObject instanceof HttpContent......");
            HttpContent httpContent = (HttpContent) httpObject;
            URI uri = new URI(this.request.uri());
            logger.info("uri.getPath()={}", uri.getPath());

            // 上传url
            if (uri.getPath().startsWith("/hsserv/upload")) {
                if (decoder != null) {
                    try{
                        decoder.offer(httpContent);
                    } catch (ErrorDataDecoderException e) {
                        responseContent.append(e.getMessage());
                        writeResponseString(ctx);
                        reset();
                        ctx.channel().close();
                        return;
                    }
                    // example of reading chunk by chunk (minimize memory usage due to Factory)
                    readHttpDataChunkByChunk(); //从解码器decoder中读出数据
                    if (httpContent instanceof LastHttpContent) {
                        responseContent.append(new JSONObject().put("code", "200").put("msg", "OK"));
                        writeResponseString(ctx);
                        readingChunks = false;
                        reset();
                    }
                } else {
                    responseContent.append(new JSONObject().put("code", "405").put("msg", "method is not support"));
                    writeResponseString(ctx);
                    ctx.channel().close();
                    return;
                }
            } else {
                logger.info("......");
                responseContent.append(new JSONObject().put("code", "200").put("msg", "url is not upload"));
                writeResponseString(ctx);
                ctx.channel().close();
                return;
            }
        } else {
            logger.info("Unknown http object");
        }
    }


    /**
     * Example of reading request by chunk and getting values from chunk to chunk
     * 从decoder中读出数据，写入临时对象，然后写入...哪里？
     * 这个封装主要是为了释放临时对象
     */
    private void readHttpDataChunkByChunk() {
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                try {
                    // new value
                    writeHttpData(data);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    data.release();
                }
            }
        }
    }

    /**
     * 设置cookie
     * 不验证名称和值的时候，可以提取公共方法
     */
    private void setCookie(HttpRequest request) {
        Set<Cookie> cookies;
        String value = request.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            // 不验证名称和值的松散实例
            cookies = ServerCookieDecoder.LAX.decode(value);
            // 严格的编码器，验证名称和值的字符是定义在RFC6265规则内
            // cookies = ServerCookieDecoder.STRICT.decode(value);
        }

        if (!cookies.isEmpty()) {
            for (Cookie cookie : cookies) {
                responseContent.append("COOKIE: " + cookie + "\r\n");
            }
        }

        responseContent.append("\r\n\r\n");
    }
    /**
     * 封装应答的回写
     * @param ctx
     */
    private void writeResponseString(ChannelHandlerContext ctx) {
        logger.info("writeResponseString={}", this.responseContent);

        // 将应答写入到ChannelBuffer
        ByteBuf buf = copiedBuffer(responseContent.toString(), CharsetUtil.UTF_8);

        // 是否关闭连接 TODO 判断是否有问题
        boolean close = HttpHeaderValues.CLOSE.equals(this.request.headers().get(HttpHeaderNames.CONNECTION))
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaderValues.KEEP_ALIVE.equals(request.headers().get(HttpHeaderNames.CONNECTION));

        // 构造应答消息体
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);

        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, buf.readableBytes());

        if (!close) {
            // There's no need to add 'Content-Length' header if this is the last response
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        // 设置cookie
        Set<Cookie> cookies;
        String value = request.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            // 不验证名称和值的松散实例
            // cookies = ServerCookieDecoder.LAX.decode(value);
            // 严格的编码器，验证名称和值的字符是定义在RFC6265规则内
            cookies = ServerCookieDecoder.STRICT.decode(value);
        }

        if (!cookies.isEmpty()) {
            for (Cookie cookie : cookies) {
                response.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
            }
        }

        // 输出内容
        try {
            ChannelFuture channelFuture = ctx.channel().writeAndFlush(response);
            if (close) {
                channelFuture.addListener(ChannelFutureListener.CLOSE).sync();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void reset() {
        request = null;
        // destroy the decoder to release all resources
        decoder.destroy();
        decoder = null;
    }

    private void writeHttpData(InterfaceHttpData data) throws IOException {
        // Attribute就是form表单里带的各种 name= 的属性
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            String name = attribute.getName();
            String value = attribute.getValue();
            logger.info("name - " + name + ", value - " + value);
        } else if (data.getHttpDataType() == HttpDataType.InternalAttribute){
            // ...
        } else if (data.getHttpDataType() == HttpDataType.FileUpload){
            String uploadFileName = getUploadFileName(data);
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                logger.info("data - " + data);
                logger.info("File name: " + fileUpload.getFilename() + ", length - " + fileUpload.length());
                logger.info("File isInMemory - " + fileUpload.isInMemory()); // tells if the file is in Memory or on File
                logger.info("File rename to ..."); // fileUpload.renameTo(File file); // enable to move into another File dest
                // decoder.removeFileUploadFromClean(fileUpload); //remove the File of to delete file
                File dir = new File("F:\\store" + File.separator);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                File dest = new File(dir, uploadFileName);
                try {
                    fileUpload.renameTo(dest);
                    decoder.removeHttpDataFromClean(fileUpload);
                    logger.info("File rename over......");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                logger.info("File to be continued......");
            }
        }
    }

    private String getUploadFileName(InterfaceHttpData data) {
        String content = data.toString();
        logger.info("content={}", content);
        String temp = content.substring(0, content.indexOf("\n"));
        content = temp.substring(temp.lastIndexOf("=") + 2, temp.lastIndexOf("\""));
        return content;
    }

}
