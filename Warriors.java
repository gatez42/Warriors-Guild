package abd;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.concurrent.Callable;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.powerbot.script.Condition;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Script;
import org.powerbot.script.Tile;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Component;
import org.powerbot.script.rt4.Game.Tab;
import org.powerbot.script.rt4.GameObject;
import org.powerbot.script.rt4.GroundItem;
import org.powerbot.script.rt4.Item;
import org.powerbot.script.rt4.Npc;
import org.powerbot.script.rt4.TilePath;
import org.powerbot.script.rt4.Widget;

@Script.Manifest(description = "Only attacks armour and collect tokens for now.",
name = "Warriors Guild",
properties = "author=abd1; topic=-1; client=4;")
public class Warriors extends PollingScript<ClientContext>{

	private final int TOKENS = 8851;
	private boolean startScript = false;
	private JFrame gui;
	private int minFood, targetTokens, eatAt;
	private Armour armour;
	private boolean foodFromBank;
	private String foodName;
	private TilePath pathToBank, pathToShop;
	private Npc targetNpc = null;
	private Tile dropTile = null;
	
	private final Tile[] BANK_PATH = {
			new Tile(2864, 35466, 0),
			new Tile(2852, 3547, 0),
			new Tile(2844, 3543, 0)
	},
			SHOP_PATH = {
					new Tile(2864, 35466, 0),
					new Tile(2852, 3547, 0),
					new Tile(2840, 3550, 0)
	};
	
	private void init(String armour, int eatAt, int minFood, int targetTokens, boolean foodFromBank, String foodName){
		this.armour = Armour.getArmour(armour);
		this.eatAt = eatAt;
		this.minFood = minFood;
		this.targetTokens = targetTokens;
		this.foodFromBank = foodFromBank;
		this.foodName = foodName;
		pathToBank = ctx.movement.newTilePath(BANK_PATH);
		pathToShop = ctx.movement.newTilePath(SHOP_PATH);
		gui.dispose();
		startScript = true;
	}
	
	@Override
	public void start() {
		if(!ctx.game.loggedIn()){
			ctx.controller.stop();
			return;
		}
		checkHealth();
		try {
			gui = creatGUI();
			gui.setVisible(true);
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {}
	}

	@Override
	public void poll() {
		if(!startScript)
			return;
		State state = getState();
		if(state == null)
			return;
		checkDrops();
		eat();
		switch(state){
		case BANK:
			if(!ctx.bank.opened()){
				if(!inBank()){
					pathToBank.traverse();
					return;
				}else{
					ctx.camera.turnTo(ctx.bank.nearest());
					ctx.bank.open();
					return;
				}
			}
			if(!hasFullArmour(true)){
				if(!hasFullArmour(false)){
					ctx.controller.stop();
					return;
				}
				if(!hasItem(armour.helm, false))
					ctx.bank.withdraw(armour.helm, 1);
				if(!hasItem(armour.body, false))
					ctx.bank.withdraw(armour.body, 1);
				if(!hasItem(armour.legs, false))
					ctx.bank.withdraw(armour.legs, 1);
				
			}
			if(ctx.inventory.select().id(995).peek().stackSize() < 5000)
				ctx.bank.withdraw(995, 10000);
			if(foodFromBank && getFoodCount() < minFood){
				Item food = ctx.bank.select().name(foodName).poll();
				if(!ctx.bank.select().name(foodName).isEmpty())
					ctx.bank.withdraw(food, minFood - getFoodCount());
				else
					ctx.controller.stop();	
			}
			break;
		case SHOP:
			if(ctx.bank.opened())
				ctx.bank.close();
			if(!inShop()){
				pathToShop.traverse();
				if(inBank())
					ctx.movement.newTilePath(new Tile(2840, 3550, 0)).traverse();
			}else{
				Widget shop = ctx.widgets.select().id(300).poll();
				boolean shopOpen = shop.component(1).component(1).text().equalsIgnoreCase("warrior guild food shop");
				if(!shopOpen){
					ctx.npcs.select().name("Lidio").peek().interact("Trade");
					Condition.wait(new Callable<Boolean>() {			
						@Override
						public Boolean call() throws Exception {
							return false;
						}
					}
					,1500, 1);
				}else{
					Component potato = shop.component(2).component(4);
					Component stew = shop.component(2).component(5);
					Component pizza = shop.component(2).component(3);
					if(potato.itemStackSize() > 0)
						potato.interact("Buy 10");
					else if(stew.itemStackSize() > 0)
						stew.interact("Buy 10");
					else if(pizza.itemStackSize() > 0)
						pizza.interact("Buy 10");
					
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return false;
						}
					}, 400, 1);
				}
			}				
			break;
		case WALK_TO_ANIMATING_ROOM:
			Widget shop = ctx.widgets.select().id(300).poll();
			boolean shopOpen = shop.component(1).component(1).text().equalsIgnoreCase("warrior guild food shop");
			if(shopOpen)
				shop.component(1).component(11).click();
			if(ctx.players.local().tile().x() != 2854 && ctx.players.local().tile().y() != 3546)
				ctx.movement.newTilePath(new Tile(2854, 3546, 0)).traverse();
			else{			
				GameObject door = ctx.objects.select().nearest().id(24306).peek();
				ctx.camera.turnTo(door);
				door.interact("Open");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !inAnimatingRoom();
					}
				}, 1000, 3);
			}
			break;
		case ANIMATE:
			//23955
			if(targetNpc == null){
				GameObject animator = ctx.objects.select().id(23955).nearest().peek();
				if(!animator.inViewport())
					ctx.camera.turnTo(animator);
				if(hasFullArmour(true)){
					ctx.inventory.select().id(armour.body).peek().interact("Use");
					ctx.objects.select().id(23955).nearest().peek().interact("Use");
						Condition.wait(new Callable<Boolean>() {
							
							@Override
							public Boolean call() throws Exception {
								return false;
							}
						}, 3000, 1);					
				}else{				
					Npc npc = ctx.npcs.select().id(armour.npc).peek();
					if(npc.interacting().equals(ctx.players.local()) || ctx.players.local().interacting().equals(npc))
						if(npc.interact("Attack"))
							targetNpc = npc;
				}
			}else{
				if(!targetNpc.valid())
					targetNpc = null;
			}
			break;
		case LOOT:
			for(GroundItem i : ctx.groundItems.select().at(dropTile))
				if(i != null)
					i.interact("Take");
			if(ctx.groundItems.select().at(dropTile).isEmpty()){
				dropTile = null;
				if(ctx.inventory.select().id(TOKENS).peek().stackSize() >= targetTokens)//TODO remove this after you add Cyclops
					ctx.controller.stop();
			}
			break;
		case EXIT_ANIMATING_ROOM:
			GameObject door = ctx.objects.select().nearest().id(24306).peek();
			ctx.camera.turnTo(door);
			door.interact("Open");
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return false;
				}
			}, 4000, 1);
			break;
		}
	}
	private State getState(){
		if(!inAnimatingRoom() && (!hasFullArmour(true)
			|| ctx.inventory.select().id(995).peek().stackSize() < 5000
			|| (getFoodCount() < minFood && foodFromBank))){
				return State.BANK;
		} else if(!inAnimatingRoom() && (getFoodCount() < minFood && !foodFromBank)){
				return State.SHOP;
		} else if(!inAnimatingRoom()){
			return State.WALK_TO_ANIMATING_ROOM;
		} else if(inAnimatingRoom() && dropTile != null){//TODO when he runs out of food he exits the without looting. check it
			return State.LOOT;
		} else if(inAnimatingRoom() && getFoodCount() != 0){
			return State.ANIMATE;
		} else if(inAnimatingRoom() && (getFoodCount() == 0 || !hasFullArmour(true)) && !ctx.players.local().inCombat()){
			return State.EXIT_ANIMATING_ROOM;
		}
		return null;
	}
	private enum State{
		BANK,SHOP,WALK_TO_ANIMATING_ROOM, ANIMATE, EXIT_ANIMATING_ROOM, LOOT
	}
	private void checkDrops(){
		if(!ctx.groundItems.select().id(TOKENS).isEmpty() && dropTile == null){
			dropTile = ctx.groundItems.select().id(8851).peek().tile();
		}
	}
	private void eat(){
		if(ctx.combat.health() <= eatAt){
			Item food = getFood();
			if(food != null){
				food.interact("Eat");
				if(food.id() == 2003)
					ctx.inventory.select().id(1923).peek().interact("Drop");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return false;
					}
				}, 300, 1);
			}
		}
	}
	private Item getFood(){
		for(Item i : ctx.inventory.items())
			if(isFood(i))
				return i;
		return null;
	}
	private boolean isFood(Item item){
		if(item == null)
			return false;
		for(String s : item.actions())
			if(s != null)
				if(s.equalsIgnoreCase("Eat"))
					return true;
		return false;
	}
	private int getFoodCount(){
		int count = 0;
		for(int i = 0;i < 28; i++){
			Item item = ctx.inventory.itemAt(i);
			if(item != null && isFood(item))
				count++;
		}
		return count;
	}
	private boolean hasItem(int id, boolean bank){
		if(bank)
			return hasItem(id, false) || !ctx.bank.select().id(id).isEmpty();
		else
			return !ctx.inventory.select().id(id).isEmpty();
	}
	private boolean hasFullArmour(boolean inInv){
		if(armour == null)
			return false;
		if(armour == null){
			ctx.controller.stop();
			return false;
		}
		if(inInv){
			return hasItem(armour.helm, false) && hasItem(armour.body, false) && hasItem(armour.legs, false);
		}else{
			return hasItem(armour.helm, true) && hasItem(armour.body, true) && hasItem(armour.legs, true);
		}
	}
	private boolean inGuild(){
		int x = ctx.players.local().tile().x();
		int y = ctx.players.local().tile().y();
		return  x >= 2838 && y >= 3537 && x <= 2875 && y <= 3555;
	}
	private boolean inShop(){
		int x = ctx.players.local().tile().x();
		int y = ctx.players.local().tile().y();
		return  x >= 2838 && y >= 2548 && x <= 2843 && y <= 3555;
	}
	private boolean inBank(){
		int x = ctx.players.local().tile().x();
		int y = ctx.players.local().tile().y();
		return  x >= 2843 && y >= 3537 && x <= 2848 && y <= 3545;
	}
	private boolean inAnimatingRoom(){
		int x = ctx.players.local().tile().x();
		int y = ctx.players.local().tile().y();
		return  x >= 2849 && y >= 3534 && x <= 2861 && y <= 3545;
	}
	@Override
	public void stop() {
		if(gui != null)
			gui.dispose();
		Condition.sleep();
		super.stop();
	}
	
	private int getMaxHealth(){
		if(!ctx.game.loggedIn())
			return -1;		
		int max = 0;
		try {
			max = Integer.parseInt(ctx.widgets.select().id(320).peek().component(9).component(4).text());
		} catch (NumberFormatException e) {
			max = 0;
		}
		/*if(max == 0){
			ctx.game.tab(Tab.STATS);
			ctx.game.tab(Tab.INVENTORY);
			return getMaxHealth();
		}		*/
		return max;
	}
	private void checkHealth(){
		ctx.game.tab(Tab.STATS);
		ctx.game.tab(Tab.INVENTORY);
	}
	private void setEatAt(int percent){
		double p = (double)percent / 100D;
		int eatAt =  (int)(getMaxHealth() * p);
		textEatAt.setText(""+eatAt);
	}
	private JPanel contentPane;
	private JSlider slider;
	private JLabel lblFoodName;
	private JRadioButton rdbtnBuyFromShop;
	private JRadioButton rdbtnFromBank;
	private final ButtonGroup buttonGroup = new ButtonGroup();
	private JTextField textTokens;
	private JTextField textEatAt;
	private JTextField textFoodName;
	private JTextField textFoodAmt;
	private JComboBox<String> comboBox;
	private JButton btnStart;

	private JFrame creatGUI() throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException{
		JFrame frame = new JFrame();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				ctx.controller.stop();
			}
		});
		frame.setResizable(false);
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		frame.setTitle("Warriors Guild");
		frame.setBounds(100, 100, 530, 343);
		try {
			frame.setIconImage(Toolkit.getDefaultToolkit().createImage(new URL("https://s24.postimg.org/qt2kcbnrp/defender.png")));
		} catch (MalformedURLException e) {}
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu mnNewMenu = new JMenu("File");
		menuBar.add(mnNewMenu);
		
		JSeparator separator = new JSeparator();
		mnNewMenu.add(separator);
		
		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent arg0) {
				ctx.controller.stop();
			}
		});
		mnNewMenu.add(mntmExit);
		
		JMenu mnHelp = new JMenu("Help");
		menuBar.add(mnHelp);
		
		JMenuItem mntmAbout = new JMenuItem("About");
		mnHelp.add(mntmAbout);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		frame.setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(0, 0, 524, 293);
		contentPane.add(tabbedPane);
		
		JPanel panelGeneral = new JPanel();
		tabbedPane.addTab("General", null, panelGeneral, null);
		panelGeneral.setLayout(null);
		
		if(!inGuild()){
		JLabel lblWarning = new JLabel("Only start inside the warriors guild!");
		lblWarning.setIcon(new ImageIcon(GUI.class.getResource("/javax/swing/plaf/metal/icons/ocean/warning.png")));
		lblWarning.setBounds(101, 83, 316, 32);
		panelGeneral.add(lblWarning);
	}
		
		btnStart = new JButton("Start");
		btnStart.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try{
				String armour = comboBox.getItemAt(comboBox.getSelectedIndex());
				int eatAt = Integer.parseInt(textEatAt.getText());
				int minFood = Integer.parseInt(textFoodAmt.getText());
				int tokens = Integer.parseInt(textTokens.getText());
				boolean foodFromBank = rdbtnFromBank.isSelected();
				String foodName = textFoodName.getText();
				init(armour, eatAt, minFood, tokens, foodFromBank, foodName);
				}catch(Exception _e){}
			}
		});
		if(!inGuild())
			btnStart.setEnabled(false);
		btnStart.setBounds(138, 188, 242, 66);
		panelGeneral.add(btnStart);
		
		JLabel lblAmountOfTokens = new JLabel("Tokens target:");
		lblAmountOfTokens.setBounds(10, 11, 139, 20);
		panelGeneral.add(lblAmountOfTokens);
		
		textTokens = new JTextField();
		textTokens.setText("500");
		textTokens.setBounds(147, 11, 44, 20);
		panelGeneral.add(textTokens);
		textTokens.setColumns(10);
		
		JLabel lblArmour = new JLabel("Armour:");
		lblArmour.setBounds(10, 42, 46, 20);
		panelGeneral.add(lblArmour);
		
		comboBox = new JComboBox<String>();
		comboBox.setModel(new DefaultComboBoxModel<String>(new String[] {"Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune"}));
		comboBox.setSelectedIndex(4);
		comboBox.setBounds(61, 42, 130, 20);
		panelGeneral.add(comboBox);
		
		JPanel panelFood = new JPanel();
		tabbedPane.addTab("Food", null, panelFood, null);
		panelFood.setLayout(null);
		
		JLabel lblEatat = new JLabel("EatAt:");
		lblEatat.setBounds(16, 42, 37, 19);
		panelFood.add(lblEatat);
		
		textEatAt = new JTextField();
		textEatAt.setEditable(false);
		setEatAt(50);
		textEatAt.setBounds(59, 41, 37, 20);
		panelFood.add(textEatAt);
		textEatAt.setColumns(10);
		
		slider = new JSlider();
		slider.setBorder(new LineBorder(Color.GRAY));
		slider.setMinorTickSpacing(5);
		slider.setPaintLabels(true);
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(10);
		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();
		table.put(0, new JLabel("0"));
		for(int i = 10; i < 100;i+=10)
			table.put(i, new JLabel(i+""));
		table.put(100, new JLabel("100%"));
		slider.setLabelTable(table);
		slider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				setEatAt(slider.getValue());
			}
		});
		slider.setBounds(16, 71, 499, 51);
		panelFood.add(slider);
		
		lblFoodName = new JLabel("Food name:");
		lblFoodName.setEnabled(false);
		lblFoodName.setBounds(59, 195, 63, 23);
		panelFood.add(lblFoodName);
		
		textFoodName = new JTextField();
		textFoodName.setEnabled(false);
		textFoodName.setBounds(132, 196, 109, 20);
		panelFood.add(textFoodName);
		textFoodName.setColumns(10);
		
		JSeparator separator_1 = new JSeparator();
		separator_1.setBounds(16, 130, 499, 2);
		panelFood.add(separator_1);
		
		rdbtnBuyFromShop = new JRadioButton("Buy from shop");
		rdbtnBuyFromShop.setSelected(true);
		buttonGroup.add(rdbtnBuyFromShop);
		rdbtnBuyFromShop.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				toggleFoodID();
			}
		});
		rdbtnBuyFromShop.setBounds(16, 139, 109, 23);
		panelFood.add(rdbtnBuyFromShop);
		
		rdbtnFromBank = new JRadioButton("From bank");
		rdbtnFromBank.setSelected(true);
		buttonGroup.add(rdbtnFromBank);
		rdbtnFromBank.addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				toggleFoodID();
			}
		});
		rdbtnFromBank.setBounds(16, 165, 109, 23);
		panelFood.add(rdbtnFromBank);
		
		JLabel lblMinimumFoodAmount = new JLabel("Minimum food amount:");
		lblMinimumFoodAmount.setBounds(16, 12, 114, 19);
		panelFood.add(lblMinimumFoodAmount);
		
		textFoodAmt = new JTextField();
		textFoodAmt.setText("10");
		textFoodAmt.setBounds(132, 11, 28, 20);
		panelFood.add(textFoodAmt);
		textFoodAmt.setColumns(10);
	
		JPanel panelPotions = new JPanel();
		tabbedPane.addTab("Potions", null, panelPotions, "Coming soon...");
		tabbedPane.setEnabledAt(2, false);
		
		JPanel panelCyclops = new JPanel();
		tabbedPane.addTab("Cyclops", null, panelCyclops, "Coming soon...");
		tabbedPane.setEnabledAt(3, false);
		return frame;
	}
	private void toggleFoodID(){
		boolean enable = !textFoodName.isEnabled();
		textFoodName.setEnabled(enable);
		lblFoodName.setEnabled(enable);
	}
}
