package com.petshop;

import com.petshop.service.EmailService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTests {
    @Test void staffInvitationIsDeliveredAsHtmlOverLocalSmtp() throws Exception {
        try (var server = new LocalSmtp()) {
            var sender = new JavaMailSenderImpl(); sender.setHost("127.0.0.1"); sender.setPort(server.socket.getLocalPort());
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout","3000");
            var beans = new DefaultListableBeanFactory(); beans.registerSingleton("mailSender",sender);
            var service = new EmailService(beans.getBeanProvider(JavaMailSender.class));
            ReflectionTestUtils.setField(service,"from","no-reply@petshop.test");
            ReflectionTestUtils.setField(service,"host","127.0.0.1");
            String link="http://localhost:8080/reset-password?token=test-one-time-token";
            service.sendStaffInvitation("staff@petshop.test","Đạt <Admin>",link);
            String wire=server.message.get(5,TimeUnit.SECONDS);
            var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(wire.getBytes(StandardCharsets.US_ASCII)));
            assertEquals("staff@petshop.test",message.getRecipients(Message.RecipientType.TO)[0].toString());
            assertTrue(message.getSubject().contains("Mời nhận tài khoản nhân viên"));
            String html=message.getContent().toString();
            assertTrue(html.contains("Đạt &lt;Admin&gt;"));
            assertTrue(html.contains(link)); assertTrue(html.contains("30 phút"));
            assertFalse(html.contains("Mật khẩu tạm"));
        }
    }

    @Test void missingSmtpConfigurationIsAnError() {
        var service=new EmailService(new DefaultListableBeanFactory().getBeanProvider(JavaMailSender.class));
        ReflectionTestUtils.setField(service,"from",""); ReflectionTestUtils.setField(service,"host","");
        assertThrows(IllegalStateException.class,()->service.sendStaffInvitation("staff@petshop.test","Staff","unused"));
    }

    static class LocalSmtp implements AutoCloseable {
        final ServerSocket socket;
        final ExecutorService executor=Executors.newSingleThreadExecutor();
        final Future<String> message;
        LocalSmtp() throws IOException {
            socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1")); socket.setSoTimeout(5000);
            message=executor.submit(()-> {
                try(var connection=socket.accept(); var reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.US_ASCII));
                    var writer=new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(),StandardCharsets.US_ASCII))) {
                    connection.setSoTimeout(5000); reply(writer,"220 localhost SMTP test");
                    var body=new StringBuilder(); String line; boolean data=false;
                    while((line=reader.readLine())!=null) {
                        if(data) { if(line.equals(".")) {data=false; reply(writer,"250 accepted");} else body.append(line.startsWith("..")?line.substring(1):line).append("\r\n"); }
                        else if(line.startsWith("EHLO") || line.startsWith("HELO")) reply(writer,"250 localhost");
                        else if(line.equals("DATA")) {data=true; reply(writer,"354 send message");}
                        else if(line.equals("QUIT")) {reply(writer,"221 bye"); break;}
                        else reply(writer,"250 OK");
                    }
                    return body.toString();
                }
            });
        }
        static void reply(BufferedWriter writer,String text) throws IOException {writer.write(text+"\r\n");writer.flush();}
        public void close() throws IOException {socket.close();executor.shutdownNow();}
    }
}
