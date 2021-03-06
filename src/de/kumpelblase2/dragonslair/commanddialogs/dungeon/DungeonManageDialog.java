package de.kumpelblase2.dragonslair.commanddialogs.dungeon;

import org.bukkit.ChatColor;
import org.bukkit.conversations.*;
import de.kumpelblase2.dragonslair.commanddialogs.GeneralConfigDialog;

public class DungeonManageDialog extends ValidatingPrompt
{
	private final String[] options = new String[]{ "create", "delete", "list", "edit", "back" };

	@Override
	public String getPromptText(final ConversationContext context)
	{
		context.getForWhom().sendRawMessage(ChatColor.GREEN + "What do you want to do?");
		return ChatColor.AQUA + "create, list, delete, edit, back";
	}

	@Override
	protected Prompt acceptValidatedInput(final ConversationContext context, final String input)
	{
		if(input.equals("create"))
			return new DungeonCreateDialog();
		else if(input.startsWith("list"))
		{
			if(input.contains(" "))
				return new DungeonListDialog(Integer.parseInt(input.split("\\ ")[1]) - 1);
			else
				return new DungeonListDialog();
		}
		else if(input.equals("edit"))
			return new DungeonEditDialog();
		else if(input.equals("back"))
			return new GeneralConfigDialog();
		else if(input.equals("delete"))
			return new DungeonDeleteDialog();

		return END_OF_CONVERSATION;
	}

	@Override
	protected boolean isInputValid(final ConversationContext arg0, final String arg1)
	{
		if(arg1.contains(" "))
		{
			final String split[] = arg1.split("\\ ");
			if(!split[0].equals("list"))
				return false;
			else if(split.length > 2)
				return false;
			else
			{
				try
				{
					Integer.parseInt(split[1]);
					return true;
				}
				catch(final Exception e)
				{
					return false;
				}
			}
		}
		else for(final String option : this.options)
		{
			if(option.equals(arg1))
				return true;
		}

		return false;
	}
}