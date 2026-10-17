%global debug_package %{nil}

Name:           notesy
Version:        1.0
Release:        1
Summary:        Native desktop sticky notes with reminders
Group:          Productivity/Office/Other
BuildArch:      noarch
License:        GPL-3.0-or-later
URL:            https://github.com/GnuJason/notesy
Source0:        %{name}-%{version}.tar.gz
Source1:        %{name}.desktop
Source2:        %{name}.appdata.xml
BuildRequires:  java-21-openjdk-devel
BuildRequires:  openjfx17-gnujason
BuildRequires:  make
BuildRequires:  appstream-glib
BuildRequires:  desktop-file-utils
Requires:       java-21-openjdk
Requires:       openjfx17-gnujason
Requires:       sqlite-jdbc
Requires:       /usr/bin/notify-send

%description
Notesy provides editable desktop notes with persistent placement, colors,
and optional reminders. It uses the system Java runtime and JavaFX libraries.
Optional egg, bomb and toaster reminder visuals are disabled by default and
do not change reminder scheduling or notification delivery.

%prep
%autosetup

%build
export JAVA_HOME=%{_jvmdir}/java-21-openjdk
if ! test -x "$JAVA_HOME/bin/javac"; then
	export JAVA_HOME=/usr/lib64/jvm/java-21-openjdk
fi
export SOURCE_DATE_EPOCH=1790294400
%make_build JAVAFX_LIB=%{_javadir}/openjfx17-gnujason

%install
install -Dm0644 target/%{name}-%{version}.jar %{buildroot}%{_datadir}/%{name}/%{name}.jar
install -Dm0755 packaging/%{name} %{buildroot}%{_bindir}/%{name}
install -Dm0644 %{SOURCE1} %{buildroot}%{_datadir}/applications/%{name}.desktop
install -Dm0644 %{SOURCE2} %{buildroot}%{_datadir}/metainfo/%{name}.appdata.xml
install -Dm0644 packaging/%{name}.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg
for size in 32 64 128 256; do
	install -Dm0644 src/main/resources/org/stickynotefx/icons/%{name}-${size}.png %{buildroot}%{_datadir}/icons/hicolor/${size}x${size}/apps/%{name}.png
done

%check
desktop-file-validate %{buildroot}%{_datadir}/applications/%{name}.desktop
appstream-util validate-relax --nonet %{buildroot}%{_datadir}/metainfo/%{name}.appdata.xml

%files
%license LICENSE
%doc README.md docs/ARCHITECTURE.md docs/MAINTAINERS.md docs/TASKS.md docs/ARTWORK.md docs/READINESS.md
%{_bindir}/%{name}
%{_datadir}/%{name}/
%{_datadir}/applications/%{name}.desktop
%{_datadir}/metainfo/%{name}.appdata.xml
%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg
%{_datadir}/icons/hicolor/*x*/apps/%{name}.png

%changelog
* Sat Sep 26 2026 Notesy maintainers - 1.0-0
- Apply GPL-3.0-or-later licensing and include artwork provenance.
- Activate existing profiles without network IPC; retain release version 1.0.
- Add historical database compatibility and system-library verification.