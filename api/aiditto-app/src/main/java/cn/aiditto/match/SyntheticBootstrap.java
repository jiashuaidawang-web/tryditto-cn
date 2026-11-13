package cn.aiditto.match;

import cn.aiditto.infra.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Loads the fictional pool once at startup when demo mode is on. Real users are left untouched. */
@Component
@ConditionalOnProperty(name = "aiditto.synthetic.autoload", havingValue = "true", matchIfMissing = true)
public class SyntheticBootstrap implements ApplicationRunner {
    private final SyntheticLoader loader;
    private final AppProperties props;

    public SyntheticBootstrap(SyntheticLoader loader, AppProperties props) {
        this.loader = loader;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.demoMode()) loader.load();
    }
}
