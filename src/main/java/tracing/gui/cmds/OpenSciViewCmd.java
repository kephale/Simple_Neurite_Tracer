package tracing.gui.cmds;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.iview.SciView;
import tracing.SNTService;

/**
 * Command for resetting SNT Preferences.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false)
public class OpenSciViewCmd implements Command {
    @Parameter
    private SciView sciView;

    @Parameter
	private SNTService sntService;

    @Override
    public void run() {
        sntService.setSciView(sciView);
    }
}
