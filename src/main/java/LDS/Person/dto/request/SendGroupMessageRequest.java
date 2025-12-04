package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送群聊消息请求 DTO
 */
@Getter
@Setter
@ApiModel(description = "发送群聊消息请求")
public class SendGroupMessageRequest {

    @ApiModelProperty(value = "群组 ID", required = true, example = "702334670")
    private Long groupId;

    @ApiModelProperty(value = "消息文本内容", required = true, example = "测试消息")
    private String text;
}
