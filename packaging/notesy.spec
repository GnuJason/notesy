Name:           notesy
Version:        1.0
Release:        0
Summary:        Native desktop sticky notes with reminders
License:        LicenseRef-UNLICENSED
Source0:        %{name}-%{version}.tar.gz
Source1:        %{name}.desktop
Source2:        %{name}.appdata.xml
BuildRequires:  java-21-openjdk-devel
BuildRequires:  openjfx
BuildRequires:  make
BuildRequires:  appstream-glib
BuildRequires:  desktop-file-utils
Requires:       java-21-openjdk
Requires:       openjfx
Requires:       sqlite-jdbc
Requires:       libnotify-tools

%description
Notesy provides editable desktop notes with persistent placement, colors,
and optional reminders. It uses the system Java runtime and JavaFX libraries.
Animated reminder themes are reserved for a later release.

%prep
%autosetup

%build
export JAVA_HOME=%{_jvmdir}/java-21-openjdk
export SOURCE_DATE_EPOCH=1790294400
%make_build JAVAFX_LIB=%{_javadir}/openjfx

%install
install -Dm0644 target/%{name}-%{version}.jar %{buildroot}%{_datadir}/%{name}/%{name}.jar
sed 's|@LIBDIR@|%{_libdir}|g' packaging/%{name} > target/%{name}
install -Dm0755 target/%{name} %{buildroot}%{_bindir}/%{name}
install -Dm0644 %{SOURCE1} %{buildroot}%{_datadir}/applications/%{name}.desktop
install -Dm0644 %{SOURCE2} %{buildroot}%{_datadir}/metainfo/%{name}.appdata.xml
install -Dm0644 packaging/%{name}.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg

%check
desktop-file-validate %{buildroot}%{_datadir}/applications/%{name}.desktop
appstream-util validate-relax --nonet %{buildroot}%{_datadir}/metainfo/%{name}.appdata.xml

%files
%doc README.md docs/ARCHITECTURE.md docs/MAINTAINERS.md docs/TASKS.md
%{_bindir}/%{name}
%{_datadir}/%{name}/
%{_datadir}/applications/%{name}.desktop
%{_datadir}/metainfo/%{name}.appdata.xml
%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg

%changelog