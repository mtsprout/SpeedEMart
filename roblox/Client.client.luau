-- QUIK-STOP: Night Shift (Roblox port, first pass)
-- Client script: HUD, order ticket, change panel, toasts, key bindings.
local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local UIS = game:GetService("UserInputService")
local TweenService = game:GetService("TweenService")

local plr = Players.LocalPlayer
local playerGui = plr:WaitForChild("PlayerGui")

local ToastEvt = RS:WaitForChild("Toast") :: RemoteEvent
local SnapEvt = RS:WaitForChild("Snap") :: RemoteEvent
local EndEvt = RS:WaitForChild("EndShift") :: RemoteEvent
local ChangeEvt = RS:WaitForChild("Change") :: RemoteEvent
local SubmitEvt = RS:WaitForChild("SubmitChange") :: RemoteEvent
local DropEvt = RS:WaitForChild("DropItem") :: RemoteEvent
local ReassureEvt = RS:WaitForChild("Reassure") :: RemoteEvent

local function mk(class: string, props: { [string]: any }, parent: Instance?): any
	local o = Instance.new(class)
	for k, v in pairs(props) do
		(o :: any)[k] = v
	end
	o.Parent = parent
	return o
end

local FONT = Enum.Font.FredokaOne
local DARK = Color3.fromRGB(24, 24, 36)
local WHITE = Color3.new(1, 1, 1)
local GREEN = Color3.fromRGB(110, 230, 140)
local YELLOW = Color3.fromRGB(255, 215, 80)
local RED = Color3.fromRGB(255, 100, 90)

local gui = mk("ScreenGui", { Name = "QuikHUD", ResetOnSpawn = false, IgnoreGuiInset = false }, playerGui)

local function card(pos: UDim2, size: UDim2, anchor: Vector2?): Frame
	local f = mk("Frame", { Position = pos, Size = size, AnchorPoint = anchor or Vector2.new(0, 0), BackgroundColor3 = DARK, BackgroundTransparency = 0.2 }, gui)
	mk("UICorner", { CornerRadius = UDim.new(0, 12) }, f)
	mk("UIStroke", { Color = Color3.fromRGB(10, 10, 16), Thickness = 2 }, f)
	return f
end

local function text(parent: Instance, t: string, size: number, color: Color3, pos: UDim2, sz: UDim2, align: Enum.TextXAlignment?): TextLabel
	return mk("TextLabel", {
		Text = t,
		TextSize = size,
		TextColor3 = color,
		Font = FONT,
		BackgroundTransparency = 1,
		Position = pos,
		Size = sz,
		TextXAlignment = align or Enum.TextXAlignment.Left,
		TextWrapped = true,
	}, parent)
end

local function meter(parent: Instance, y: number, name: string, col: Color3): (Frame, TextLabel)
	text(parent, name, 16, WHITE, UDim2.fromOffset(10, y), UDim2.fromOffset(60, 20))
	local bg = mk("Frame", { Position = UDim2.fromOffset(70, y + 3), Size = UDim2.fromOffset(150, 14), BackgroundColor3 = Color3.fromRGB(50, 50, 66) }, parent)
	mk("UICorner", { CornerRadius = UDim.new(0, 7) }, bg)
	local fill = mk("Frame", { Size = UDim2.fromScale(1, 1), BackgroundColor3 = col }, bg)
	mk("UICorner", { CornerRadius = UDim.new(0, 7) }, fill)
	local pct = text(parent, "", 14, WHITE, UDim2.fromOffset(226, y), UDim2.fromOffset(44, 20))
	return fill, pct
end

-- top-left: health + rage
local topLeft = card(UDim2.fromOffset(12, 12), UDim2.fromOffset(282, 82))
local healthFill, healthPct = meter(topLeft, 8, "HEALTH", GREEN)
local rageFill, ragePct = meter(topLeft, 32, "RAGE", RED)
local carryLbl = text(topLeft, "Carrying: nothing", 15, YELLOW, UDim2.fromOffset(10, 56), UDim2.fromOffset(262, 20))

-- top-right: tips
local topRight = card(UDim2.new(1, -12, 0, 12), UDim2.fromOffset(160, 44), Vector2.new(1, 0))
local tipsLbl = text(topRight, "TIPS $0", 24, GREEN, UDim2.fromOffset(0, 0), UDim2.fromScale(1, 1), Enum.TextXAlignment.Center)

-- bottom-right: clock
local clockCard = card(UDim2.new(1, -12, 1, -12), UDim2.fromOffset(200, 92), Vector2.new(1, 1))
local clockLbl = text(clockCard, "10:00 PM", 30, WHITE, UDim2.fromOffset(0, 4), UDim2.new(1, 0, 0, 36), Enum.TextXAlignment.Center)
local nightLbl = text(clockCard, "NIGHT 1", 16, YELLOW, UDim2.fromOffset(0, 40), UDim2.new(1, 0, 0, 20), Enum.TextXAlignment.Center)
local phaseLbl = text(clockCard, "", 14, Color3.fromRGB(190, 190, 210), UDim2.fromOffset(0, 62), UDim2.new(1, 0, 0, 20), Enum.TextXAlignment.Center)

-- bottom-left: order ticket
local ticket = card(UDim2.new(0, 12, 1, -50), UDim2.fromOffset(290, 118), Vector2.new(0, 1))
text(ticket, "ORDER TICKET", 14, Color3.fromRGB(190, 190, 210), UDim2.fromOffset(10, 4), UDim2.fromOffset(200, 18))
local wantsLbl = text(ticket, "No customer yet", 22, WHITE, UDim2.fromOffset(10, 22), UDim2.fromOffset(270, 56))
local stepLbl = text(ticket, "", 16, YELLOW, UDim2.fromOffset(10, 78), UDim2.fromOffset(270, 36))

-- bottom controls strip
local strip = text(gui, "E: interact (hold)    Q: put down    F: \"Be right with you!\"    H: help", 14, Color3.fromRGB(220, 220, 235), UDim2.new(0, 12, 1, -40), UDim2.new(1, -240, 0, 24))
strip.AnchorPoint = Vector2.new(0, 0)
strip.TextStrokeTransparency = 0.5

-- toast
local toastLbl = mk("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 70),
	Size = UDim2.fromOffset(560, 40),
	BackgroundColor3 = DARK,
	BackgroundTransparency = 0.25,
	TextColor3 = WHITE,
	Font = FONT,
	TextSize = 22,
	TextWrapped = true,
	Visible = false,
}, gui)
mk("UICorner", { CornerRadius = UDim.new(0, 10) }, toastLbl)
local toastId = 0
local function showToast(t: string, col: Color3?)
	toastId += 1
	local my = toastId
	toastLbl.Text = t
	toastLbl.TextColor3 = col or WHITE
	toastLbl.Visible = true
	task.delay(3.2, function()
		if toastId == my then
			toastLbl.Visible = false
		end
	end)
end
ToastEvt.OnClientEvent:Connect(showToast)

-- snap overlay
local snapFrame = mk("Frame", { Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.fromRGB(200, 0, 0), BackgroundTransparency = 1, Visible = false, ZIndex = 20 }, gui)
local snapLbl = mk("TextLabel", {
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.45),
	Size = UDim2.fromScale(0.9, 0.3),
	BackgroundTransparency = 1,
	TextColor3 = Color3.fromRGB(255, 240, 80),
	TextStrokeColor3 = Color3.new(0, 0, 0),
	TextStrokeTransparency = 0,
	Font = FONT,
	TextScaled = true,
	Text = "SNAP!!!",
	ZIndex = 21,
}, snapFrame)
SnapEvt.OnClientEvent:Connect(function(t: string)
	snapLbl.Text = t
	snapFrame.Visible = true
	snapFrame.BackgroundTransparency = 0.55
	snapLbl.Rotation = -6
	TweenService:Create(snapFrame, TweenInfo.new(2.2), { BackgroundTransparency = 1 }):Play()
	TweenService:Create(snapLbl, TweenInfo.new(0.4, Enum.EasingStyle.Elastic), { Rotation = 4 }):Play()
	task.delay(2.4, function()
		snapFrame.Visible = false
	end)
end)

-- end-of-shift overlay
local endFrame = mk("Frame", { Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.35, Visible = false, ZIndex = 30 }, gui)
local endTitle = text(endFrame, "", 48, YELLOW, UDim2.fromScale(0, 0.3), UDim2.fromScale(1, 0.15), Enum.TextXAlignment.Center)
local endSub = text(endFrame, "", 26, WHITE, UDim2.fromScale(0, 0.47), UDim2.fromScale(1, 0.1), Enum.TextXAlignment.Center)
local endHint = text(endFrame, "Next shift starts in a few seconds...", 20, Color3.fromRGB(200, 200, 215), UDim2.fromScale(0, 0.6), UDim2.fromScale(1, 0.06), Enum.TextXAlignment.Center)
endTitle.ZIndex = 31
endSub.ZIndex = 31
endHint.ZIndex = 31
EndEvt.OnClientEvent:Connect(function(title: string?, sub: string?, win: boolean?)
	if title == nil then
		endFrame.Visible = false
		return
	end
	endTitle.Text = title
	endTitle.TextColor3 = win and GREEN or RED
	endSub.Text = sub or ""
	endFrame.Visible = true
end)

-- help panel
local help = card(UDim2.fromScale(0.5, 0.5), UDim2.fromOffset(520, 400), Vector2.new(0.5, 0.5))
help.Visible = false
help.ZIndex = 25
local helpText = text(
	help,
	"QUIK-STOP: NIGHT SHIFT\n\n"
		.. "Survive 8 hours (10 PM to 6 AM). Keep your health up and your rage down.\n\n"
		.. "E (hold)  interact: take items, ring up, mop\nQ  put down what you're carrying\nF  \"Be right with you!\" (slows impatience, has a cooldown)\nH  show / hide this help\n\n"
		.. "Customers shop for themselves. When they reach the counter: RING THEM UP at the register (hold E), then make exact change.\n"
		.. "Blue cap: energy drink. Check their ID first (hold E).\n"
		.. "Red cap: batteries are on the TOP shelf behind you. Climb the step stool (hold E), then hand them over.\n"
		.. "Grab batteries BEFORE they reach the counter for a PROACTIVE bonus.\n"
		.. "Big bills ($50/$100) for small snacks will make you mad. That's the point.\n"
		.. "Spills: mop them or your health drains.",
	17,
	WHITE,
	UDim2.fromOffset(16, 12),
	UDim2.fromOffset(488, 376)
)
helpText.ZIndex = 26
helpText.TextYAlignment = Enum.TextYAlignment.Top

-- change panel
local panel = card(UDim2.fromScale(0.5, 0.5), UDim2.fromOffset(380, 340), Vector2.new(0.5, 0.5))
panel.Visible = false
panel.ZIndex = 15
local panelInfo = text(panel, "", 22, WHITE, UDim2.fromOffset(14, 10), UDim2.fromOffset(352, 70), Enum.TextXAlignment.Center)
local panelTotal = text(panel, "Change: $0", 34, GREEN, UDim2.fromOffset(14, 84), UDim2.fromOffset(352, 44), Enum.TextXAlignment.Center)
local total = 0
local function setTotal(v: number)
	total = v
	panelTotal.Text = "Change: $" .. total
end
local function btn(label: string, pos: UDim2, size: UDim2, col: Color3, cb: () -> ())
	local b = mk("TextButton", { Text = label, Position = pos, Size = size, BackgroundColor3 = col, TextColor3 = DARK, Font = FONT, TextSize = 24 }, panel)
	mk("UICorner", { CornerRadius = UDim.new(0, 8) }, b)
	b.MouseButton1Click:Connect(cb)
end
local bills = { 1, 5, 10, 20, 50 }
for i, v in ipairs(bills) do
	btn("+$" .. v, UDim2.fromOffset(14 + (i - 1) * 72, 140), UDim2.fromOffset(66, 50), Color3.fromRGB(130, 210, 140), function()
		setTotal(total + v)
	end)
end
btn("Clear", UDim2.fromOffset(14, 206), UDim2.fromOffset(110, 46), Color3.fromRGB(240, 200, 110), function()
	setTotal(0)
end)
btn("GIVE CHANGE", UDim2.fromOffset(134, 206), UDim2.fromOffset(232, 46), Color3.fromRGB(100, 190, 250), function()
	SubmitEvt:FireServer(total, false)
	setTotal(0)
end)
btn("Step away", UDim2.fromOffset(14, 268), UDim2.fromOffset(352, 44), Color3.fromRGB(200, 200, 215), function()
	panel.Visible = false
	setTotal(0)
	SubmitEvt:FireServer(0, true)
end)

ChangeEvt.OnClientEvent:Connect(function(what: string, price: number?, paid: number?)
	if what == "open" and price and paid then
		panelInfo.Text = "Item costs $" .. price .. "\nThey paid with $" .. paid
		setTotal(0)
		panel.Visible = true
	else
		panel.Visible = false
		setTotal(0)
	end
end)

-- keys
UIS.InputBegan:Connect(function(input, processed)
	if processed then
		return
	end
	if input.KeyCode == Enum.KeyCode.Q then
		DropEvt:FireServer()
	elseif input.KeyCode == Enum.KeyCode.F then
		ReassureEvt:FireServer()
	elseif input.KeyCode == Enum.KeyCode.H then
		help.Visible = not help.Visible
	end
end)
task.delay(1, function()
	help.Visible = true
	task.delay(14, function()
		help.Visible = false
	end)
end)

-- HUD refresh
local function ga(name: string): any
	return workspace:GetAttribute(name)
end

task.spawn(function()
	while true do
		task.wait(0.1)
		local health = (ga("Health") or 100) :: number
		local rage = (ga("Rage") or 0) :: number
		healthFill.Size = UDim2.fromScale(math.clamp(health / 100, 0, 1), 1)
		healthPct.Text = math.floor(health) .. "%"
		rageFill.Size = UDim2.fromScale(math.clamp(rage / 100, 0, 1), 1)
		rageFill.BackgroundColor3 = rage >= 80 and Color3.fromRGB(255, 40, 40) or RED
		ragePct.Text = math.floor(rage) .. "%"
		tipsLbl.Text = "TIPS $" .. tostring(ga("Tips") or 0)

		local m = math.floor((ga("Clock") or 0) :: number)
		local tot = 22 * 60 + m
		local h = math.floor(tot / 60) % 24
		local mi = tot % 60
		local ap = h >= 12 and "PM" or "AM"
		local h12 = h % 12
		if h12 == 0 then
			h12 = 12
		end
		clockLbl.Text = string.format("%d:%02d %s", h12, mi, ap)
		nightLbl.Text = "NIGHT " .. tostring(ga("Night") or 1)
		phaseLbl.Text = tostring(ga("Phase") or "")

		local carry = plr:GetAttribute("Carry")
		carryLbl.Text = (carry and carry ~= "") and ("Carrying: " .. tostring(carry)) or "Carrying: nothing"

		local want = tostring(ga("FrontWant") or "")
		local stage = tostring(ga("FrontStage") or "")
		if want ~= "" then
			wantsLbl.Text = "WANTS: " .. want
			if stage == "id" then
				stepLbl.Text = "NEXT: check their ID at the register"
			elseif stage == "ring" then
				stepLbl.Text = "NEXT: ring it up at the register"
			elseif stage == "item" then
				if carry == want then
					stepLbl.Text = "NEXT: hand it over at the register"
				elseif want == "Batteries" then
					stepLbl.Text = "NEXT: climb the step stool for batteries"
				else
					stepLbl.Text = "NEXT: fetch " .. want
				end
			elseif stage == "pay" then
				stepLbl.Text = "NEXT: make exact change at the register"
			else
				stepLbl.Text = ""
			end
		elseif stage == "walking" then
			wantsLbl.Text = "Customer walking up..."
			stepLbl.Text = ""
		else
			wantsLbl.Text = "No customer at the counter"
			stepLbl.Text = ""
		end
	end
end)
