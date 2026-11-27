package com.yhs.livestreaming.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.yhs.livestreaming.architecture.fixture.AllowedMemberConsumer;
import com.yhs.livestreaming.architecture.fixture.ForbiddenMemberConsumer;
import com.yhs.livestreaming.global.archfixture.ForbiddenGlobalDependency;
import com.yhs.livestreaming.member.app.api.MemberAccountStatus;
import com.yhs.livestreaming.member.app.api.MemberQueryApi;
import com.yhs.livestreaming.member.app.api.MemberSummary;
import com.yhs.livestreaming.member.app.api.archfixture.LeakyPublicContract;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import org.junit.jupiter.api.Test;

/**
 * GL-T019: 모듈 경계(G-D015). 운영 코드 전체(테스트 제외)를 읽어 규칙을 검사하고, 규칙이 실제 위반을 잡는지 테스트 전용
 * 대상(fixture)으로 따로 확인한다. app 모듈 테스트 클래스패스에 모든 도메인 모듈이 있다.
 */
class ModuleBoundaryTest {

    private static final String BASE = "com.yhs.livestreaming";
    private static final String[] DOMAIN_MODULES = {
        BASE + ".member..", BASE + ".broadcast..", BASE + ".streaming..", BASE + ".chat..",
        BASE + ".comment..", BASE + ".donation..", BASE + ".recording.."
    };

    /** member 밖의 코드는 member 중 공개 패키지(`member.app.api`)만 참조한다(MB-D029). */
    static final ArchRule ONLY_MEMBER_PUBLIC_API = noClasses()
            .that().resideOutsideOfPackage(BASE + ".member..")
            .should().dependOnClassesThat(resideInAPackage(BASE + ".member..")
                    .and(not(resideInAPackage(BASE + ".member.app.api.."))))
            .as("member 밖의 코드는 member.app.api 패키지만 참조한다(G-D015)");

    /** global은 도메인 모듈을 참조하지 않는다(G-D001). */
    static final ArchRule GLOBAL_DOES_NOT_DEPEND_ON_DOMAINS = noClasses()
            .that().resideInAPackage(BASE + ".global..")
            .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_MODULES)
            .as("global은 도메인 모듈을 참조하지 않는다(G-D015, G-D001)");

    /** 공개 패키지는 member 내부 타입(도메인 enum·엔티티 등)을 드러내지 않는다: 프로젝트 안에서는 자기 패키지만 참조한다. */
    static final ArchRule PUBLIC_API_IS_SELF_CONTAINED = noClasses()
            .that().resideInAPackage(BASE + ".member.app.api..")
            .should().dependOnClassesThat(resideInAPackage(BASE + "..")
                    .and(not(resideInAPackage(BASE + ".member.app.api.."))))
            .as("member.app.api는 member 내부·다른 모듈 타입을 드러내지 않는다(G-D015, MB-D029)");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void productionClassesOfEveryModuleAreImported() { // 클래스를 못 읽어 규칙이 빈 집합에 통과하는 일을 막는다
        org.assertj.core.api.Assertions.assertThat(PRODUCTION.contain(Member.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(PRODUCTION.contain(MemberQueryApi.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(
                PRODUCTION.contain(com.yhs.livestreaming.global.auth.AuthenticatedMember.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(PRODUCTION.contain(ForbiddenMemberConsumer.class)).isFalse();
        org.assertj.core.api.Assertions.assertThat(PRODUCTION.contain(ForbiddenGlobalDependency.class)).isFalse();
        org.assertj.core.api.Assertions.assertThat(PRODUCTION.contain(LeakyPublicContract.class)).isFalse();
    }

    @Test
    void otherModulesUseOnlyTheMemberPublicApi() {
        ONLY_MEMBER_PUBLIC_API.check(PRODUCTION);
    }

    @Test
    void globalDoesNotDependOnDomainModules() {
        GLOBAL_DOES_NOT_DEPEND_ON_DOMAINS.check(PRODUCTION);
    }

    @Test
    void publicApiExposesNoInternalTypes() {
        PUBLIC_API_IS_SELF_CONTAINED.check(PRODUCTION);
    }

    @Test
    void ruleCatchesGlobalDependingOnADomainModule() { // 공개 인터페이스라도 global에서는 참조할 수 없다
        JavaClasses violating = new ClassFileImporter()
                .importClasses(ForbiddenGlobalDependency.class, MemberQueryApi.class);

        assertThatThrownBy(() -> GLOBAL_DOES_NOT_DEPEND_ON_DOMAINS.check(violating)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(ForbiddenGlobalDependency.class.getName());
    }

    @Test
    void ruleCatchesAPublicContractLeakingInternalTypes() {
        JavaClasses leaky = new ClassFileImporter().importClasses(LeakyPublicContract.class, AccountStatus.class);
        JavaClasses clean = new ClassFileImporter().importClasses(MemberQueryApi.class, MemberSummary.class,
                MemberAccountStatus.class);

        assertThatThrownBy(() -> PUBLIC_API_IS_SELF_CONTAINED.check(leaky)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(LeakyPublicContract.class.getName());
        assertThatCode(() -> PUBLIC_API_IS_SELF_CONTAINED.check(clean)).doesNotThrowAnyException();
    }

    @Test
    void ruleCatchesAReferenceToMemberInternals() { // 규칙이 비어 있지 않고 위반을 실제로 잡는지
        JavaClasses forbidden = new ClassFileImporter().importClasses(ForbiddenMemberConsumer.class, Member.class);
        JavaClasses allowed = new ClassFileImporter()
                .importClasses(AllowedMemberConsumer.class, MemberQueryApi.class, MemberSummary.class);

        assertThatThrownBy(() -> ONLY_MEMBER_PUBLIC_API.check(forbidden)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(ForbiddenMemberConsumer.class.getName());
        assertThatCode(() -> ONLY_MEMBER_PUBLIC_API.check(allowed)).doesNotThrowAnyException();
    }
}
