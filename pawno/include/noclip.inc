#ifndef __ANTI_NOCLIP_INC__
#define __ANTI_NOCLIP_INC__

new Float:LastPos[MAX_PLAYERS][3];
new bool:IsTeleported[MAX_PLAYERS];

#define NOCLIP_KICK(playerid) do { \
    SAM(COLOR_GREEN, "AdmCmd: %s was autokicked by %s, reason: Teleport hacks", GetRPName(playerid), SERVER_BOT); \
    Kick(playerid, "Teleport hacks"); \
} while(0)

// Call this every update
public AN_UpdatePlayer(playerid)
{
    new Float:x, y, z;
    GetPlayerPos(playerid, x, y, z);

    // Distance moved since last tick
    new Float:dx = fabs(x - LastPos[playerid][0]);
    new Float:dy = fabs(y - LastPos[playerid][1]);
    new Float:dz = fabs(z - LastPos[playerid][2]);

    if ((dx > 5.0 || dy > 5.0 || dz > 5.0) && !IsTeleported[playerid])
    {
        NOCLIP_KICK(playerid);
        return 0;
    }

    // Save last position
    LastPos[playerid][0] = x;
    LastPos[playerid][1] = y;
    LastPos[playerid][2] = z;

    return 1;
}

// Call this when you legitimately teleport a player
public AN_SetTeleported(playerid)
{
    IsTeleported[playerid] = true;
    SetTimerEx("AN_ResetTeleport", 3000, false, "i", playerid);
}

public AN_ResetTeleport(playerid)
{
    IsTeleported[playerid] = false;
}

#endif
