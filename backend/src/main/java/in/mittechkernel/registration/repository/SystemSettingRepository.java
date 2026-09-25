package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** The singleton settings row. Seeded by V8, so it always exists. */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Boolean> {
}
