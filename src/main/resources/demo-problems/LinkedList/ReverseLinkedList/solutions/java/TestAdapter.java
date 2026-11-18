import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAdapter implements com.codeprep.api.TestAdapter {
    @Override
    public JsonNode execute(JsonNode input, ObjectMapper mapper) {
        JsonNode values = input.get("head");
        ListNode head = null, tail = null;
        for (JsonNode value : values) {
            ListNode node = new ListNode(value.asInt());
            if (head == null) head = node;
            else tail.next = node;
            tail = node;
        }
        ListNode reversed = new Solution().reverseList(head);
        var result = mapper.createArrayNode();
        while (reversed != null) {
            result.add(reversed.val);
            reversed = reversed.next;
        }
        return result;
    }
}
