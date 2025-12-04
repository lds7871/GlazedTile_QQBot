package LDS.Person.tasks.MsgLisLogic;

import LDS.Person.dto.request.SendGroupImageRequest;
import LDS.Person.util.ImgToUri;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * VIP 截取窗口并发送图片的逻辑类
 * 处理 "指令截取->(内容)" 指令
 */
@Component
@Slf4j
public class VIPScreenshotLogic {

    @Autowired
    private RestTemplate restTemplate;

    private static final String SEND_IMAGE_URL = "http://localhost:8090/api/ncat/send/group-image";

    /**
     * 从指令中提取窗口标题
     * 例如: "指令截取->任务管理器" -> "任务管理器"
     *      "指令截取->管理员: AST" -> "管理员: AST"
     * 
     * @param rawMessage 原始消息
     * @return 窗口标题，如果提取失败返回 null
     */
    public String extractWindowTitle(String rawMessage) {
        try {
            String prefix = "指令截取->";
            int startIndex = rawMessage.indexOf(prefix);
            if (startIndex == -1) {
                return null;
            }

            // 从 "指令截取->" 之后开始提取到字符串末尾或下一个换行
            String windowTitle = rawMessage.substring(startIndex + prefix.length()).trim();
            
            // 如果包含换行符，只取第一行
            int newlineIndex = windowTitle.indexOf("\n");
            if (newlineIndex > 0) {
                windowTitle = windowTitle.substring(0, newlineIndex).trim();
            }
            
            return windowTitle.isEmpty() ? null : windowTitle;

        } catch (Exception e) {
            log.error("提取窗口标题失败", e);
            return null;
        }
    }

    /**
     * 处理截取指令：截取窗口 -> 转换为 Data URI -> 发送图片
     * 
     * @param groupId 群组ID
     * @param windowTitle 窗口标题
     */
    public void handleScreenshotCommand(Long groupId, String windowTitle) {
        try {
            // log.info("开始处理截取指令 - 窗口标题: {}, 群ID: {}", windowTitle, groupId);

            // 1. 截取窗口并转换为 Data URI
            String dataUri = ImgToUri.processWindow(windowTitle);

            if (dataUri == null || dataUri.isEmpty()) {
                log.warn("截取失败或转换失败 - 窗口标题: {}", windowTitle);
                return;
            }

            // log.info("窗口截取成功，Data URI 大小: {} KB", String.format("%.2f", dataUri.length() / 1024.0));

            // 2. 调用发送图片接口
            sendImageToGroup(groupId, dataUri);

        } catch (Exception e) {
            log.error("处理截取指令异常 - 窗口标题: {}, 群ID: {}", windowTitle, groupId, e);
        }
    }

    /**
     * 调用 NCatSendMessageController 的 /group-image 接口发送 Data URI 图片
     * 
     * @param groupId 群组ID
     * @param dataUri Data URI 字符串
     */
    private void sendImageToGroup(Long groupId, String dataUri) {
        try {
            SendGroupImageRequest request = new SendGroupImageRequest();
            request.setGroupId(groupId);
            request.setFile(dataUri);

            // log.info("准备发送 Data URI 图片到群ID: {}", groupId);

            ResponseEntity<?> response = restTemplate.postForEntity(
                SEND_IMAGE_URL,
                request,
                Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // log.info("Data URI 图片发送成功 - 群ID: {}", groupId);
            } else {
                log.warn(" Data URI 图片发送失败 - 群ID: {}, 状态码: {}", groupId, response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用发送图片接口异常 - 群ID: {}", groupId, e);
        }
    }
}
