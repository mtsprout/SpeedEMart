-- QUIK-STOP: Night Shift  (Roblox port, first pass)
-- Server script: builds the store, runs customers, rage, cash, shifts.
local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local Lighting = game:GetService("Lighting")
local Debris = game:GetService("Debris")

------------------------------------------------------------ remotes
local function newRemote(name: string): RemoteEvent
	local r = Instance.new("RemoteEvent")
	r.Name = name
	r.Parent = RS
	return r
end
local ToastEvt = newRemote("Toast")
local SnapEvt = newRemote("Snap")
local EndEvt = newRemote("EndShift")
local ChangeEvt = newRemote("Change")
local SubmitEvt = newRemote("SubmitChange")
local DropEvt = newRemote("DropItem")
local ReassureEvt = newRemote("Reassure")

------------------------------------------------------------ helpers
local function new(class: string, props: { [string]: any }, parent: Instance?): any
	local o = Instance.new(class)
	for k, v in pairs(props) do
		(o :: any)[k] = v
	end
	o.Parent = parent
	return o
end

local function toast(plr: Player?, text: string, color: Color3?)
	local col = color or Color3.fromRGB(255, 255, 255)
	if plr then
		ToastEvt:FireClient(plr, text, col)
	else
		ToastEvt:FireAllClients(text, col)
	end
end

local YELLOW = Color3.fromRGB(255, 220, 90)
local RED = Color3.fromRGB(255, 110, 100)
local GREEN = Color3.fromRGB(130, 240, 150)

------------------------------------------------------------ the store
local FY = 0.4 -- floor top height
local store = new("Folder", { Name = "Store" }, workspace)
local spillFolder = new("Folder", { Name = "Spills" }, store)

local function P(name: string, size: Vector3, pos: Vector3, color: Color3, mat: Enum.Material?, parent: Instance?): Part
	return new("Part", {
		Name = name,
		Anchored = true,
		Size = size,
		Position = pos,
		Color = color,
		Material = mat or Enum.Material.SmoothPlastic,
		TopSurface = Enum.SurfaceType.Smooth,
		BottomSurface = Enum.SurfaceType.Smooth,
	}, parent or store)
end

local function prompt(parent: Instance, action: string, object: string, hold: number, dist: number): ProximityPrompt
	return new("ProximityPrompt", {
		ActionText = action,
		ObjectText = object,
		HoldDuration = hold,
		MaxActivationDistance = dist,
		RequiresLineOfSight = false,
	}, parent)
end

local function tag3d(part: Part, text: string, offsetY: number, color: Color3?)
	local bb = new("BillboardGui", {
		Size = UDim2.fromOffset(160, 30),
		StudsOffset = Vector3.new(0, offsetY, 0),
		MaxDistance = 45,
		AlwaysOnTop = false,
	}, part)
	new("TextLabel", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		Text = text,
		TextColor3 = color or Color3.new(1, 1, 1),
		TextStrokeTransparency = 0.3,
		Font = Enum.Font.FredokaOne,
		TextScaled = true,
	}, bb)
end

-- ground, floor, walls
P("Ground", Vector3.new(300, 1, 300), Vector3.new(0, -0.5, 0), Color3.fromRGB(70, 74, 82), Enum.Material.Asphalt)
P("Floor", Vector3.new(60, 0.4, 50), Vector3.new(0, FY / 2, 0), Color3.fromRGB(236, 232, 214), Enum.Material.Marble)
local WALL = Color3.fromRGB(255, 205, 90)
local WH = 14
P("WallBack", Vector3.new(61, WH, 1), Vector3.new(0, FY + WH / 2, -25), WALL)
P("WallLeft", Vector3.new(1, WH, 51), Vector3.new(-30, FY + WH / 2, 0), WALL)
P("WallRight", Vector3.new(1, WH, 51), Vector3.new(30, FY + WH / 2, 0), WALL)
P("WallFrontL", Vector3.new(24, WH, 1), Vector3.new(-18, FY + WH / 2, 25), WALL)
P("WallFrontR", Vector3.new(24, WH, 1), Vector3.new(18, FY + WH / 2, 25), WALL)
P("Lintel", Vector3.new(12, 4, 1), Vector3.new(0, FY + WH - 2, 25), WALL)
local ceil = P("Ceiling", Vector3.new(60, 0.5, 50), Vector3.new(0, FY + WH + 0.25, 0), Color3.fromRGB(240, 240, 240))
ceil.Transparency = 0.7
ceil.CanCollide = false

local sign = P("Sign", Vector3.new(24, 3.4, 1), Vector3.new(0, FY + WH + 1.7, 25), Color3.fromRGB(200, 30, 40), Enum.Material.Neon)
do
	local sg = new("SurfaceGui", { Face = Enum.NormalId.Back, SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud }, sign)
	new("TextLabel", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		Text = "QUIK-STOP",
		TextColor3 = Color3.new(1, 1, 1),
		Font = Enum.Font.FredokaOne,
		TextScaled = true,
	}, sg)
end

for _, x in ipairs({ -18, 0, 18 }) do
	for _, z in ipairs({ -16, 0, 16 }) do
		local lamp = P("Lamp", Vector3.new(5, 0.3, 5), Vector3.new(x, FY + WH - 0.2, z), Color3.fromRGB(255, 250, 225), Enum.Material.Neon)
		new("PointLight", { Range = 30, Brightness = 1.3, Color = Color3.fromRGB(255, 244, 220) }, lamp)
	end
end

-- spawn pad (staff side)
do
	local sp = new("SpawnLocation", {
		Name = "StaffSpawn",
		Anchored = true,
		Size = Vector3.new(6, 0.2, 4),
		CFrame = CFrame.new(0, FY + 0.1, -1.2) * CFrame.Angles(0, math.pi, 0),
		Transparency = 1,
		CanCollide = false,
		Neutral = true,
		Duration = 0,
	}, store)
	sp.TopSurface = Enum.SurfaceType.Smooth
end

-- counter + register
P("Counter", Vector3.new(14, 3.4, 2.2), Vector3.new(0, FY + 1.7, 2), Color3.fromRGB(190, 120, 70), Enum.Material.WoodPlanks)
local register = P("Register", Vector3.new(2.2, 1.1, 1.5), Vector3.new(0, FY + 3.95, 2), Color3.fromRGB(60, 70, 85))
tag3d(register, "REGISTER", 1.8)
local regPrompt = prompt(register, "Serve", "Customer", 0.3, 13)
regPrompt.Enabled = false

-- items
local ITEMS: { [string]: { color: Color3 } } = {
	Chips = { color = Color3.fromRGB(255, 140, 40) },
	Candy = { color = Color3.fromRGB(255, 100, 190) },
	Gum = { color = Color3.fromRGB(90, 220, 120) },
	Cookies = { color = Color3.fromRGB(150, 100, 60) },
	["Energy Drink"] = { color = Color3.fromRGB(0, 180, 255) },
	Batteries = { color = Color3.fromRGB(240, 200, 40) },
}
local SNACKS = { "Chips", "Candy", "Gum", "Cookies" }

local function setCarry(plr: Player, name: string?)
	plr:SetAttribute("Carry", name or "")
	if not name then
		plr:SetAttribute("Early", false)
	end
	local ch = plr.Character
	if not ch then
		return
	end
	local old = ch:FindFirstChild("CarryItem")
	if old then
		old:Destroy()
	end
	local hrp = ch:FindFirstChild("HumanoidRootPart") :: BasePart?
	if name and hrp and ITEMS[name] then
		local p = new("Part", {
			Name = "CarryItem",
			Size = Vector3.new(1.4, 1.4, 1.4),
			Color = ITEMS[name].color,
			CanCollide = false,
			Massless = true,
			Material = Enum.Material.SmoothPlastic,
			CFrame = hrp.CFrame * CFrame.new(0, 0.4, -2.2),
		}, ch)
		new("WeldConstraint", { Part0 = hrp, Part1 = p }, p)
	end
end

-- free-standing shelves out on the shop floor; customers shop from both sides
local SHOP: { [string]: { stand: Vector3, look: Vector3, via: Vector3? } } = {}
local function addGondola(x: number, z0: number, z1: number, westName: string, eastName: string)
	local zc = (z0 + z1) / 2
	local len = z1 - z0
	P("Gondola", Vector3.new(3, 6, len), Vector3.new(x, FY + 3, zc), Color3.fromRGB(150, 150, 165))
	P("GondolaTop", Vector3.new(3.4, 0.4, len + 0.4), Vector3.new(x, FY + 6.2, zc), Color3.fromRGB(70, 70, 85))
	for side = 1, 2 do
		local name = side == 1 and westName or eastName
		local fx = x + (side == 1 and -1.9 or 1.9)
		for row = 0, 2 do
			for k = 0, math.floor(len / 2) - 1 do
				P("Stock_" .. name, Vector3.new(1.2, 1.4, 1.5), Vector3.new(fx, FY + 1.1 + row * 1.9, z0 + 1.2 + k * 2), ITEMS[name].color)
			end
		end
		local sg = P("ShelfSign_" .. name, Vector3.new(0.4, 0.4, 0.4), Vector3.new(fx, FY + 7.6, zc), Color3.new(1, 1, 1))
		sg.Transparency = 1
		tag3d(sg, name, 0)
		local sx = x + (side == 1 and -4.5 or 4.5)
		SHOP[name] = { stand = Vector3.new(sx, 3, zc), look = Vector3.new(x, 3, zc), via = Vector3.new(sx, 3, 8) }
	end
end
addGondola(-22, 14, 24, "Cookies", "Chips")
addGondola(20, 14, 24, "Candy", "Gum")

-- energy drink cooler island
do
	local zc = 12
	P("CoolerIsland", Vector3.new(3, 8, 8), Vector3.new(-9, FY + 4, zc), Color3.fromRGB(60, 120, 205), Enum.Material.Glass)
	P("CoolerTop", Vector3.new(3.4, 0.4, 8.4), Vector3.new(-9, FY + 8.2, zc), Color3.fromRGB(30, 60, 120))
	for row = 0, 3 do
		for k = 0, 3 do
			P("Stock_Energy", Vector3.new(1, 1.6, 1.4), Vector3.new(-7.4, FY + 1.2 + row * 1.9, zc - 3 + k * 2), ITEMS["Energy Drink"].color, Enum.Material.Neon)
		end
	end
	local sg = P("CoolerSign", Vector3.new(0.4, 0.4, 0.4), Vector3.new(-7.4, FY + 9.6, zc), Color3.new(1, 1, 1))
	sg.Transparency = 1
	tag3d(sg, "Energy Drinks", 0, Color3.fromRGB(120, 220, 255))
	SHOP["Energy Drink"] = { stand = Vector3.new(-5, 3, zc), look = Vector3.new(-9, 3, zc), via = nil }
end

-- top shelf (batteries): a proper wall rack up high, with a real step stool
do
	local DARK = Color3.fromRGB(55, 55, 70)
	P("RackBoard", Vector3.new(22, 6.4, 0.5), Vector3.new(13, FY + 10.4, -24.7), Color3.fromRGB(40, 42, 54))
	for _, dx in ipairs({ -11, 0, 11 }) do
		P("RackPost", Vector3.new(0.6, 6.4, 1.6), Vector3.new(13 + dx, FY + 10.4, -23.9), DARK, Enum.Material.Metal)
	end
	for _, y in ipairs({ 8.2, 10.9 }) do
		P("RackShelf", Vector3.new(22, 0.35, 2), Vector3.new(13, FY + y, -23.7), Color3.fromRGB(200, 200, 215), Enum.Material.Metal)
	end
	local PACKS = {
		Color3.fromRGB(240, 200, 40),
		Color3.fromRGB(220, 60, 50),
		Color3.fromRGB(50, 120, 230),
		Color3.fromRGB(60, 180, 90),
	}
	for row, y in ipairs({ 8.2, 10.9 }) do
		for i = 0, 11 do
			local col = PACKS[(i + row) % #PACKS + 1]
			local x = 3.2 + i * 1.7
			P("BatteryPack", Vector3.new(1.3, 2.3, 0.5), Vector3.new(x, FY + y + 1.25, -23.9), col)
			P("BatteryCell", Vector3.new(0.5, 1.1, 0.5), Vector3.new(x, FY + y + 1.0, -23.55), Color3.fromRGB(215, 215, 225), Enum.Material.Metal)
		end
	end
end
local stool = P("StepStool", Vector3.new(3, 0.4, 3), Vector3.new(13, FY + 2, -19), Color3.fromRGB(235, 235, 240))
for _, ox in ipairs({ -1.2, 1.2 }) do
	for _, oz in ipairs({ -1.2, 1.2 }) do
		P("StoolLeg", Vector3.new(0.4, 2, 0.4), Vector3.new(13 + ox, FY + 1, -19 + oz), Color3.fromRGB(150, 150, 165), Enum.Material.Metal)
	end
end
P("StoolStep", Vector3.new(2.6, 0.3, 2.6), Vector3.new(13, FY + 1, -19), Color3.fromRGB(215, 215, 225))
local stoolPrompt = prompt(stool, "Climb for batteries", "Step stool", 4.5, 8)
stoolPrompt.Enabled = false

------------------------------------------------------------ game state
local PHASES = {
	{ 0, 10, "Evening traffic" },
	{ 55, 30, "Quiet hours" },
	{ 150, 26, "Late-night lull" },
	{ 210, 18, "Energy drink rush" },
	{ 250, 28, "Quiet" },
	{ 300, 20, "Lottery fever" },
	{ 345, 30, "Dead of night" },
	{ 390, 12, "Morning commute" },
	{ 450, 9, "Final stretch" },
}
local SHIFT_LEN = 480

local S: any = {}
local oldStateModels: { Instance } = {}

local function phaseAt(t: number)
	local p = PHASES[1]
	for _, x in ipairs(PHASES) do
		if t >= x[1] then
			p = x
		end
	end
	return p
end

local function nm(): number
	return 1 + 0.10 * (S.night - 1)
end

local function addRage(x: number)
	S.rage = math.clamp(S.rage + x * (1 + 0.08 * (S.night - 1)), 0, 100)
end

local function resetState(night: number)
	for _, m in ipairs(oldStateModels) do
		if m.Parent then
			m:Destroy()
		end
	end
	table.clear(oldStateModels)
	spillFolder:ClearAllChildren()
	for _, plr in ipairs(Players:GetPlayers()) do
		setCarry(plr, nil)
	end
	S = {
		night = night,
		t = 0,
		rage = 0,
		health = 100,
		tips = 0,
		served = 0,
		walkouts = 0,
		snapT = 0,
		spawnT = 3,
		count = 0,
		queue = {},
		shoppers = {},
		running = true,
		freeze = 0,
		burst = 0,
		burstT = 0,
		warned = false,
		burstStarted = false,
		spillT = 40,
		reassureReady = 0,
		phase = "Evening traffic",
		endAt = 0,
	}
end
resetState(1)
S.running = false

------------------------------------------------------------ customers
local SKIN = { Color3.fromRGB(255, 204, 153), Color3.fromRGB(234, 184, 146), Color3.fromRGB(190, 130, 90), Color3.fromRGB(120, 80, 55) }
local SHIRTS = {
	Color3.fromRGB(231, 76, 60),
	Color3.fromRGB(52, 152, 219),
	Color3.fromRGB(46, 204, 113),
	Color3.fromRGB(155, 89, 182),
	Color3.fromRGB(241, 196, 15),
	Color3.fromRGB(230, 126, 34),
}

local function makeNPC(shirt: Color3): Model?
	local d = Instance.new("HumanoidDescription")
	local skin = SKIN[math.random(#SKIN)]
	d.HeadColor = skin
	d.TorsoColor = shirt
	d.LeftArmColor = skin
	d.RightArmColor = skin
	d.LeftLegColor = Color3.fromRGB(60, 70, 110)
	d.RightLegColor = Color3.fromRGB(60, 70, 110)
	local ok, m = pcall(function()
		return Players:CreateHumanoidModelFromDescription(d, Enum.HumanoidRigType.R6)
	end)
	if ok then
		return m :: Model
	end
	warn("QuikStop: could not build NPC:", m)
	return nil
end

local function pickKind(): string
	S.count += 1
	if S.count <= 5 then
		return "normal"
	end
	local r = math.random()
	if r < 0.22 then
		return "energy"
	elseif r < 0.40 then
		return "battery"
	elseif r < 0.58 then
		return "big"
	end
	return "normal"
end

local function frontCustomer(): any
	return S.queue[1]
end

local function leave(c: any, happy: boolean)
	local i = table.find(S.queue, c)
	if i then
		table.remove(S.queue, i)
	end
	c.stage = "leaving"
	c.panelOpen = false
	c.label.Text = happy and "Thanks!" or "Ugh, forget it!"
	c.label.TextColor3 = happy and GREEN or RED
	c.bar.Visible = false
	c.hum.WalkSpeed = 15
	c.hum:MoveTo(Vector3.new(0, 3, 42))
	Debris:AddItem(c.model, 9)
end

local function spawnCustomer(forceKind: string?)
	local mine = S
	local kind = forceKind or pickKind()
	local m = makeNPC(SHIRTS[math.random(#SHIRTS)])
	if not m then
		return
	end
	if S ~= mine or not S.running then
		m:Destroy()
		return
	end
	local hum = m:FindFirstChildOfClass("Humanoid") :: Humanoid
	local hrp = m:FindFirstChild("HumanoidRootPart") :: BasePart
	local head = m:FindFirstChild("Head") :: BasePart
	m.Name = "Customer"
	hum.WalkSpeed = 11
	hum.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None

	local want = SNACKS[math.random(#SNACKS)]
	local price = math.random(2, 9)
	local paid = price <= 5 and 5 or (price <= 10 and 10 or 20)
	local maxP = 55
	local stage = "ring"
	local text = want
	if kind == "energy" then
		want = "Energy Drink"
		price = math.random(3, 6)
		paid = 10
		maxP = 50
		stage = "id"
		text = "Energy Drink (ID check!)"
		local cap = new("Part", { Size = Vector3.new(2.2, 0.5, 2.4), Color = Color3.fromRGB(30, 90, 230), CanCollide = false, Massless = true, CFrame = head.CFrame * CFrame.new(0, 0.7, 0) }, m)
		new("WeldConstraint", { Part0 = head, Part1 = cap }, cap)
	elseif kind == "battery" then
		want = "Batteries"
		stage = "item"
		price = math.random(8, 12)
		paid = 20
		maxP = 60
		text = "Batteries (top shelf!)"
		local cap = new("Part", { Size = Vector3.new(2.2, 0.5, 2.4), Color = Color3.fromRGB(220, 50, 50), CanCollide = false, Massless = true, CFrame = head.CFrame * CFrame.new(0, 0.7, 0) }, m)
		new("WeldConstraint", { Part0 = head, Part1 = cap }, cap)
	elseif kind == "big" then
		price = math.random(1, 4)
		paid = math.random() < 0.5 and 50 or 100
		maxP = 50
		text = want .. "  (paying $" .. paid .. ")"
	end
	maxP = maxP / nm()

	local bb = new("BillboardGui", { Size = UDim2.fromOffset(210, 56), StudsOffset = Vector3.new(0, 3.4, 0), AlwaysOnTop = true, MaxDistance = 90, Adornee = head }, head)
	local label = new("TextLabel", {
		Size = UDim2.new(1, 0, 0, 34),
		BackgroundColor3 = Color3.fromRGB(20, 20, 30),
		BackgroundTransparency = 0.25,
		Text = text,
		TextColor3 = Color3.new(1, 1, 1),
		Font = Enum.Font.FredokaOne,
		TextScaled = true,
	}, bb)
	new("UICorner", { CornerRadius = UDim.new(0, 8) }, label)
	local barBg = new("Frame", { Position = UDim2.new(0, 0, 0, 38), Size = UDim2.new(1, 0, 0, 10), BackgroundColor3 = Color3.fromRGB(20, 20, 30) }, bb)
	local bar = new("Frame", { Size = UDim2.fromScale(1, 1), BackgroundColor3 = GREEN }, barBg)
	new("UICorner", { CornerRadius = UDim.new(0, 5) }, barBg)
	new("UICorner", { CornerRadius = UDim.new(0, 5) }, bar)

	m.Parent = workspace
	m:PivotTo(CFrame.new(0, 3.5, 34))
	pcall(function()
		hrp:SetNetworkOwner(nil)
	end)

	local c = {
		kind = kind,
		want = want,
		price = price,
		paid = paid,
		patience = maxP,
		maxP = maxP,
		stage = stage,
		text = text,
		shopping = kind ~= "battery",
		shopT = 0,
		heldItem = nil,
		model = m,
		hum = hum,
		hrp = hrp,
		label = label,
		bar = bar,
		bb = bb,
		arrived = false,
		faced = false,
		bigRaged = false,
		panelOpen = false,
		panelT = 0,
	}
	if c.shopping then
		c.label.Text = "Shopping..."
		table.insert(S.shoppers, c)
	else
		table.insert(S.queue, c)
	end
	table.insert(oldStateModels, m)
end

------------------------------------------------------------ spills
local function spawnSpill()
	local x = math.random(-22, 22)
	local z = math.random() < 0.5 and math.random(-20, -4) or math.random(8, 20)
	local sp = new("Part", {
		Name = "Spill",
		Anchored = true,
		CanCollide = false,
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.2, 6, 6),
		CFrame = CFrame.new(x, FY + 0.15, z) * CFrame.Angles(0, 0, math.rad(90)),
		Color = Color3.fromRGB(90, 170, 240),
		Transparency = 0.15,
		Material = Enum.Material.Glass,
	}, spillFolder)
	tag3d(sp, "SPILL!", 2, Color3.fromRGB(120, 200, 255))
	local pp = prompt(sp, "Mop", "Spill", 2, 8)
	pp.Triggered:Connect(function(plr)
		if sp.Parent then
			sp:Destroy()
			S.health = math.min(100, S.health + 6)
			S.rage = math.max(0, S.rage - 6)
			toast(plr, "Mopped! +6 health", GREEN)
		end
	end)
	toast(nil, "Somebody spilled something. Mop it!", YELLOW)
end

------------------------------------------------------------ counter prompt logic
regPrompt.Triggered:Connect(function(plr)
	if not S.running then
		return
	end
	local c = frontCustomer()
	if not c or not c.arrived then
		return
	end
	if c.stage == "id" then
		c.stage = "ring"
		c.text = c.want .. "  (ID ok)"
		c.label.Text = c.text
		toast(plr, "ID checked. Ring them up.", GREEN)
	elseif c.stage == "ring" then
		c.stage = "pay"
		if c.kind == "big" then
			addRage(14)
		end
		c.panelOpen = true
		c.panelT = 0
		ChangeEvt:FireClient(plr, "open", c.price, c.paid)
	elseif c.stage == "item" then
		local carry = plr:GetAttribute("Carry")
		if carry == c.want then
			local early = plr:GetAttribute("Early") == true
			setCarry(plr, nil)
			c.stage = "pay"
			if c.kind == "battery" and early then
				S.tips += 5
				S.health = math.min(100, S.health + 3)
				S.rage = math.max(0, S.rage - 8)
				c.patience = math.min(c.maxP, c.patience + c.maxP * 0.25)
				toast(plr, "PROACTIVE! +$5", GREEN)
			end
			if c.kind == "big" then
				addRage(14)
			end
			c.panelOpen = true
			c.panelT = 0
			ChangeEvt:FireClient(plr, "open", c.price, c.paid)
		elseif carry == "" or carry == nil then
			toast(plr, "Fetch the " .. c.want .. " first!", YELLOW)
		else
			toast(plr, "That's not what they asked for!", RED)
			setCarry(plr, nil)
			addRage(9)
			c.patience -= c.maxP * 0.15
		end
	elseif c.stage == "pay" then
		c.panelOpen = true
		c.panelT = 0
		ChangeEvt:FireClient(plr, "open", c.price, c.paid)
	end
end)

SubmitEvt.OnServerEvent:Connect(function(plr, amount, cancel)
	local c = frontCustomer()
	if not S.running or not c or c.stage ~= "pay" then
		return
	end
	if cancel == true then
		c.panelOpen = false
		return
	end
	if typeof(amount) ~= "number" then
		return
	end
	if amount == c.paid - c.price then
		local frac = math.clamp(c.patience / c.maxP, 0, 1)
		local tip = 1 + math.floor(frac * 3)
		S.tips += tip
		S.served += 1
		S.rage = math.max(0, S.rage - 7)
		ChangeEvt:FireClient(plr, "close")
		toast(plr, "+$" .. tip .. " tip", GREEN)
		leave(c, true)
	else
		c.patience -= c.maxP * 0.15
		addRage(4)
		toast(plr, "Wrong change! They're annoyed.", RED)
	end
end)

stoolPrompt.Triggered:Connect(function(plr)
	if not S.running then
		return
	end
	local wants = false
	for _, c in ipairs(S.queue) do
		if c.want == "Batteries" and c.stage ~= "leaving" then
			wants = true
		end
	end
	if not wants then
		return
	end
	if plr:GetAttribute("Carry") ~= "" then
		toast(plr, "Hands full! Press Q to put it down.", RED)
		return
	end
	local c0 = frontCustomer()
	local early = not (c0 and c0.arrived and c0.want == "Batteries")
	setCarry(plr, "Batteries")
	plr:SetAttribute("Early", early)
end)

DropEvt.OnServerEvent:Connect(function(plr)
	setCarry(plr, nil)
end)

ReassureEvt.OnServerEvent:Connect(function(plr)
	if not S.running then
		return
	end
	local now = os.clock()
	if now < S.reassureReady then
		toast(plr, "You just said that. Wait " .. math.ceil(S.reassureReady - now) .. "s.", YELLOW)
		return
	end
	S.reassureReady = now + math.min(30, 15 + 2 * (S.night - 1))
	S.freeze = math.max(2, 4 - 0.5 * (S.night - 1))
	local c = frontCustomer()
	if c then
		c.patience = math.min(c.maxP, c.patience + c.maxP * math.max(0.04, 0.12 - 0.02 * (S.night - 1)))
	end
	toast(plr, "\"Be right with you!\"", GREEN)
end)

------------------------------------------------------------ meltdown
local WORDS = { "WHACK!", "BONK!", "THWACK!", "SMACK!", "KA-POW!" }
local function meltdown()
	S.snapT = 0
	S.rage = 0
	S.health = math.max(0, S.health - 12)
	local victim = S.queue[1] or S.queue[#S.queue]
	SnapEvt:FireAllClients("SNAP!!!  " .. WORDS[math.random(#WORDS)])
	for _, c in ipairs(S.queue) do
		c.patience -= c.maxP * 0.45
	end
	if victim then
		local i = table.find(S.queue, victim)
		if i then
			table.remove(S.queue, i)
		end
		if victim.panelOpen then
			ChangeEvt:FireAllClients("close")
		end
		victim.stage = "leaving"
		victim.panelOpen = false
		victim.bb.Enabled = false
		victim.hum.PlatformStand = true
		victim.hrp.AssemblyLinearVelocity = Vector3.new(math.random(-15, 15), 55, 60)
		Debris:AddItem(victim.model, 6)
		S.walkouts += 1
		local pct = math.min(0.5, 0.25 + 0.03 * (S.night - 1))
		local loss = math.min(S.tips, math.max(10, math.floor(S.tips * pct)))
		S.tips -= loss
		toast(nil, "LAWSUIT! The customer sued. -$" .. loss .. " from your tips.", RED)
	end
end

------------------------------------------------------------ main step
local function endShift(win: boolean)
	S.running = false
	S.endAt = os.clock() + 12
	for _, c in ipairs(S.queue) do
		leave(c, true)
	end
	ChangeEvt:FireAllClients("close")
	local title = win and "SHIFT COMPLETE! It's 6 AM." or "YOU'RE FIRED!"
	local sub = string.format("Night %d  |  Tips: $%d  |  Served: %d  |  Walkouts: %d", S.night, S.tips, S.served, S.walkouts)
	EndEvt:FireAllClients(title, sub, win)
	S.win = win
end

local function step(dt: number)
	local nmv = nm()
	S.t += dt
	if S.freeze > 0 then
		S.freeze -= dt
	end
	local ph = phaseAt(S.t)
	S.phase = ph[3]

	-- spawning
	if S.t >= 198 and not S.warned then
		S.warned = true
		toast(nil, "Heads up: an energy drink rush hits at 1:30 AM!", YELLOW)
	end
	if S.t >= 210 and not S.burstStarted then
		S.burstStarted = true
		S.burst = 5 + math.floor((S.night - 1) / 2)
		toast(nil, "ENERGY DRINK RUSH!", RED)
	end
	S.spawnT -= dt
	if S.burst > 0 then
		S.burstT -= dt
		if S.burstT <= 0 then
			S.burst -= 1
			S.burstT = 2.5
			task.spawn(spawnCustomer, "energy")
		end
	elseif S.spawnT <= 0 then
		local cap = math.min(5, 3 + math.floor(S.night / 2))
		if #S.queue + #S.shoppers < cap then
			task.spawn(spawnCustomer)
		end
		S.spawnT = ph[2] / nmv * (0.8 + 0.4 * math.random())
	end

	-- spills
	S.spillT -= dt
	if S.spillT <= 0 and S.t > 30 then
		S.spillT = 45 / nmv
		spawnSpill()
	end

	-- shoppers: through the door, around the shelves, pick up their item, then join the line
	local function walkTo(c: any, v: Vector3): boolean
		local flat = (c.hrp.Position - v) * Vector3.new(1, 0, 1)
		if flat.Magnitude > 2.5 then
			c.hum:MoveTo(Vector3.new(v.X, c.hrp.Position.Y, v.Z))
			return false
		end
		return true
	end
	for _, c in ipairs(table.clone(S.shoppers)) do
		local info = SHOP[c.want]
		c.ph = c.ph or 0
		if c.ph == 0 then
			if walkTo(c, Vector3.new(0, 0, 19)) then
				c.ph = info.via and 1 or 2
			end
		elseif c.ph == 1 then
			if walkTo(c, info.via :: Vector3) then
				c.ph = 2
			end
		elseif c.ph == 2 then
			if walkTo(c, info.stand) then
				c.shopT += dt
				if c.shopT > 0.3 and not c.faced then
					c.faced = true
					c.hrp.CFrame = CFrame.lookAt(c.hrp.Position, Vector3.new(info.look.X, c.hrp.Position.Y, info.look.Z))
				end
				if c.shopT >= 1.8 then
					local it = new("Part", {
						Name = "HeldItem",
						Size = Vector3.new(1.2, 1.4, 1.2),
						Color = ITEMS[c.want].color,
						CanCollide = false,
						Massless = true,
						CFrame = c.hrp.CFrame * CFrame.new(0, 0.3, -1.8),
					}, c.model)
					new("WeldConstraint", { Part0 = c.hrp, Part1 = it }, it)
					c.faced = false
					c.ph = info.via and 3 or 4
				end
			end
		elseif c.ph == 3 then
			if walkTo(c, info.via :: Vector3) then
				c.ph = 4
			end
		else
			c.shopping = false
			c.label.Text = c.text
			table.remove(S.shoppers, table.find(S.shoppers, c))
			table.insert(S.queue, c)
		end
	end

	-- customers
	local snapshot = table.clone(S.queue)
	for _, c in ipairs(snapshot) do
		local i = table.find(S.queue, c)
		if i then
			local spot = Vector3.new(0, c.hrp.Position.Y, 6 + 3 * (i - 1))
			local flat = (c.hrp.Position - spot) * Vector3.new(1, 0, 1)
			if flat.Magnitude > 2 then
				c.hum:MoveTo(spot)
				c.arrived = false
				c.faced = false
			else
				if not c.arrived then
					c.arrived = true
				end
				if not c.faced then
					c.faced = true
					c.hrp.CFrame = CFrame.lookAt(c.hrp.Position, c.hrp.Position - Vector3.new(0, 0, 1))
				end
			end
			if i == 1 and c.arrived and c.kind == "big" and not c.bigRaged then
				c.bigRaged = true
				addRage(20)
				toast(nil, "A $" .. c.paid .. " bill for a $" .. c.price .. " snack. Great.", YELLOW)
			end
			if c.panelOpen then
				c.panelT += dt
			end
			if not (c.panelOpen and c.panelT < 20) and S.freeze <= 0 then
				c.patience -= dt
			end
			local frac = math.clamp(c.patience / c.maxP, 0, 1)
			c.bar.Size = UDim2.fromScale(frac, 1)
			c.bar.BackgroundColor3 = frac > 0.5 and GREEN or (frac > 0.25 and YELLOW or RED)
			if c.patience <= 0 then
				addRage(14)
				S.walkouts += 1
				if c.panelOpen then
					ChangeEvt:FireAllClients("close")
				end
				toast(nil, "A customer walked out!", RED)
				leave(c, false)
			end
		end
	end

	-- rage
	if #S.queue >= 4 then
		addRage(2 * dt)
	end
	S.rage = math.max(0, S.rage - 0.6 * dt)
	if S.rage >= 80 then
		S.snapT += dt
		if S.snapT >= 7 then
			meltdown()
		end
	else
		S.snapT = math.max(0, S.snapT - dt * 2)
	end

	-- health
	local drain = 0
	if S.t > 60 or S.night > 1 then
		drain += #spillFolder:GetChildren() * 0.35
		if S.rage > 70 then
			drain += 0.6
		end
	end
	if drain > 0 then
		S.health -= drain * dt * (1 + 0.08 * (S.night - 1))
	else
		S.health = math.min(100, S.health + 0.1 * dt)
	end

	-- prompts
	local c = frontCustomer()
	if c and c.arrived and c.stage ~= "leaving" then
		regPrompt.Enabled = true
		if c.stage == "id" then
			regPrompt.ActionText = "Check ID"
			regPrompt.HoldDuration = 2
		elseif c.stage == "ring" then
			regPrompt.ActionText = "Ring up " .. c.want
			regPrompt.HoldDuration = 0.5
		elseif c.stage == "item" then
			regPrompt.ActionText = "Hand over " .. c.want
			regPrompt.HoldDuration = c.kind == "battery" and 1.6 or 0.5
		else
			regPrompt.ActionText = "Make change"
			regPrompt.HoldDuration = 0.3
		end
	else
		regPrompt.Enabled = false
	end
	local wantsBat = false
	for _, q in ipairs(S.queue) do
		if q.want == "Batteries" then
			wantsBat = true
		end
	end
	stoolPrompt.Enabled = wantsBat

	-- publish
	workspace:SetAttribute("Rage", S.rage)
	workspace:SetAttribute("Health", S.health)
	workspace:SetAttribute("Tips", S.tips)
	workspace:SetAttribute("Clock", S.t)
	workspace:SetAttribute("Night", S.night)
	workspace:SetAttribute("Phase", S.phase)
	workspace:SetAttribute("Queue", #S.queue)
	workspace:SetAttribute("SnapT", S.snapT)
	if c and c.arrived then
		workspace:SetAttribute("FrontWant", c.want)
		workspace:SetAttribute("FrontStage", c.stage)
		workspace:SetAttribute("FrontPrice", c.price)
		workspace:SetAttribute("FrontPaid", c.paid)
	else
		workspace:SetAttribute("FrontWant", "")
		workspace:SetAttribute("FrontStage", c and "walking" or "")
	end
	workspace:SetAttribute("Running", true)

	Lighting.ClockTime = (22 + S.t / 60) % 24

	if S.health <= 0 then
		endShift(false)
	elseif S.t >= SHIFT_LEN then
		endShift(true)
	end
end

------------------------------------------------------------ players
local function onPlayer(plr: Player)
	plr:SetAttribute("Carry", "")
	plr.CharacterAdded:Connect(function()
		task.wait(0.2)
		setCarry(plr, nil)
	end)
end
Players.PlayerAdded:Connect(onPlayer)
for _, p in ipairs(Players:GetPlayers()) do
	onPlayer(p)
end

Lighting.Ambient = Color3.fromRGB(70, 70, 95)
Lighting.OutdoorAmbient = Color3.fromRGB(55, 55, 90)
Lighting.Brightness = 1

------------------------------------------------------------ main loop
task.spawn(function()
	while #Players:GetPlayers() == 0 do
		task.wait(0.5)
	end
	task.wait(5)
	resetState(1)
	toast(nil, "Night 1 begins. Welcome to QUIK-STOP.", YELLOW)
	local last = os.clock()
	while true do
		task.wait(0.1)
		local now = os.clock()
		local dt = math.min(now - last, 0.5)
		last = now
		if S.running then
			step(dt)
		else
			workspace:SetAttribute("Running", false)
			if S.endAt > 0 and now >= S.endAt then
				local nextNight = S.win and S.night + 1 or S.night
				resetState(nextNight)
				EndEvt:FireAllClients(nil)
				toast(nil, "Night " .. nextNight .. " begins.", YELLOW)
			end
		end
	end
end)
