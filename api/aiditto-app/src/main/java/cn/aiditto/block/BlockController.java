package cn.aiditto.block;

import cn.aiditto.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {
    private final BlockService blocks;

    public BlockController(BlockService blocks) {
        this.blocks = blocks;
    }

    @GetMapping
    List<BlockService.BlockView> list(@AuthenticationPrincipal AuthUser me) {
        return blocks.list(me.userId());
    }

    @PostMapping
    BlockService.BlockView add(@AuthenticationPrincipal AuthUser me, @RequestBody BlockService.NewBlock body) {
        return blocks.add(me.userId(), body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        blocks.delete(me.userId(), id);
    }
}
