package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送群聊图片消息请求 DTO
 */
@Getter
@Setter
@ApiModel(description = "发送群聊图片消息请求")
public class SendGroupImageRequest {

    @ApiModelProperty(value = "群组 ID", required = true, example = "702334670")
    private Long groupId;

    @ApiModelProperty(value = "图片 URL 或本地文件路径", required = true, 
            example = "建议使用网络URL呢亲")
    private String file;
}
