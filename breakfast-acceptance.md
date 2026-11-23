# Breakfast advancements for Pro

Base `c1c0802be13d0cd5a01149ffa357018f7a863c5c`  
Advancement source `5960f6f62ae929b718e56291698ccc301fd175b6` on `codex/breakfast-combined-handoff`. Reapplying the patch to a temporary index reproduced the exact source tree. The five criterion records and English titles were inspected. Compilation and gameplay checks are pending with Pro.

## Hosted acceptance

- [ ] Start in the Reach with two fresh Survival players. Keep B outside four blocks. Give A ordinary Minecraft bread. Neither breakfast advancement should unlock.
- [ ] Have A approach a fresh generated baker toaster within four blocks with interaction permission. On the pop, verify **Toast of the Town** only. Pick up the loaf and verify **Loaf at First Sight** for `uwudimension:uwu_bread`.
- [ ] **Recommended regression check.** Keep the loaf with A. Bring B to that same spent `uwudimension:baker_toaster` and right-click it. B should earn only **Toast of the Town**, with no second loaf. Then give B the original loaf and verify the separate bread award. Repeat the click and pickup to check neither toast repeats.
- [ ] Craft a Petal Harvest Galette for **Flour Arrangements**. Pick up Moonlit Potato Chowder and a Dewleaf Picnic Wrap, then eat each for **Soup There It Is** and **Lettuce Take a Break** respectively. Pickup alone should award neither eating advancement.
