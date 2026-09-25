package in.mittechkernel.registration.dto;

import in.mittechkernel.registration.entity.Team;

/** Null on a solo registration. */
public record TeamResponse(Long teamId, String name, String captainRollNo, int size) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getCaptain().getRollNo(),
                team.size());
    }
}
