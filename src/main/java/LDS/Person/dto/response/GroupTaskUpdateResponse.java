package LDS.Person.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 群组任务更新响应DTO
 */
@Getter
@Setter
@ApiModel(description = "群组任务更新响应")
public class GroupTaskUpdateResponse {
    @ApiModelProperty(value = "状态码", example = "0")
    private int code;

    @ApiModelProperty(value = "提示消息", example = "成功")
    private String message;

    @ApiModelProperty(value = "更新后的任务配置数据（包含所有字段）")
    private Map<String, Object> data;

    public GroupTaskUpdateResponse(int code, String message, Map<String, Object> data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static GroupTaskUpdateResponse success(Map<String, Object> data) {
        return new GroupTaskUpdateResponse(0, "成功", data);
    }

    public static GroupTaskUpdateResponse notFound(String message) {
        return new GroupTaskUpdateResponse(1, message, null);
    }

    public static GroupTaskUpdateResponse error(String message) {
        return new GroupTaskUpdateResponse(-1, message, null);
    }
}
