package LDS.Person.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送群聊消息响应 DTO
 */
@Getter
@Setter
@ApiModel(description = "发送群聊消息响应")
public class SendGroupMessageResponse {

    @ApiModelProperty(value = "是否发送成功")
    private boolean success;

    @ApiModelProperty(value = "响应消息")
    private String message;

    @ApiModelProperty(value = "消息 ID")
    private Integer messageId;

    @ApiModelProperty(value = "原始响应数据")
    private Object rawResponse;

    public SendGroupMessageResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public SendGroupMessageResponse(boolean success, String message, Integer messageId) {
        this.success = success;
        this.message = message;
        this.messageId = messageId;
    }

    public static SendGroupMessageResponse success(Integer messageId) {
        return new SendGroupMessageResponse(true, "发送成功", messageId);
    }

    public static SendGroupMessageResponse error(String message) {
        return new SendGroupMessageResponse(false, message);
    }
}
