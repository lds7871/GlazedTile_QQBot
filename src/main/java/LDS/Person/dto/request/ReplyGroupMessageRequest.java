package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 回复群聊消息请求 DTO
 */
@Getter
@Setter
@ApiModel(description = "回复群聊消息请求")
public class ReplyGroupMessageRequest {

    @ApiModelProperty(value = "群组 ID", required = true, example = "702334670")
    private Long groupId;

    @ApiModelProperty(value = "被回复消息的 ID", required = true, example = "1595242509")
    private String messageId;

    @ApiModelProperty(value = "回复消息文本内容", required = true, example = "回复消息")
    private String text;
}
