const O5 = 8
const O4 = 7
const O3 = 6
const O2 = 5
const O1 = 4
const MAIN = 0
const FIRST = 1
const SECOND = 2
const THIRD = 3

var positionHistory = [];
var pos_x = 0;
var pos_y = 0;
var pos_z = 0;
var z_text = "0";

let touchEventHandler = null;

// 테스트베드별 설정 값
const testbedConfigs = {
    coex: {
        x_y_axis_rotation : false,
        map_area: {
            left: '0px', top: '0px'
        },
        small_arrow: {
            width: '40px',
            left:'2px', top: '-22px',
            transformOrigin: '-3px 20px',
        },
        small_dot: {
            width: '10px',
            left: '-5px', top: '-6px',
            constant_top: {a : -1, b : 3413.01896, c : 0.65412},
            constant_left: {a : 1, b : 274.3702, c : 0.65389},
        },
        big_arrow: {
            width: '300px',
        },
        big_dot: {
            width: '300px',
            constant_top: {a : -1, b : 3191.01896, c : 0.65412},
            constant_left: {a : 1, b : 44.3702, c : 0.65389},
        },
        X: {
            width: '12px',
            left: '-8px', top: '-9px',
            constant_top: {a : -1, b : 2232, c : 1.529},
            constant_left: {a : 1, b : -180, c : 1.529},
        },
        box: {
            constant_top: {a : -1, b : 3424, c : 0.65412},
            constant_left: {a : 1, b : 285, c : 0.65389},
        }
    },
    hansando: {
        x_y_axis_rotation : true,
        map_area: {
            left: '-200px', top: '0px',
        },
        small_arrow: {
            width: '40px',
            left:'2px', top: '-22px',
            transformOrigin: '-3px 20px',
        },
        small_dot: {
            width: '10px',
            left: '-5px', top: '-6px',
            constant_top: {a : -1, b : 1555, c : 0.92613636},
            constant_left: {a : 1, b : 225, c : 0.92613636},
        },
        big_arrow: {
            width: '120px',
        },
        big_dot: {
            width: '120px',
            constant_top: {a : -1, b : 1488, c : 0.92613636},
            constant_left: {a : 1, b : 160, c : 0.92613636},
        },
        X: {
            width: '12px',
            left: '-8px', top: '-9px',
            constant_top: {a : -1, b : 1438, c : 1.0797546},
            constant_left: {a : 1, b : -210, c : 1.0797546},
        },
        box: {
            constant_top: {a : 1, b : 0, c : 1},
            constant_left: {a : 1, b : 0, c : 1},
        }
    },
    suwonstation: {
        x_y_axis_rotation : false,
        map_area: {
            left: '-200px', top: '0px',
        },
        small_arrow: {
            width: '40px',
            left:'2px', top: '-22px',
            transformOrigin: '-3px 20px',
        },
        small_dot: {
            width: '10px',
            left: '-5px', top: '-6px',
            constant_top: {a : 1, b : 296, c : 0.81},
            constant_left: {a : 1, b : -623, c : 0.81},
        },
        big_arrow: {
            width: '120px',
        },
        big_dot: {
            width: '120px',
            constant_top: {a : 0, b : 0, c : 0},
            constant_left: {a : 0, b : 0, c : 0},
        },
        X: {
            width: '12px',
            left: '-8px', top: '-9px',
            constant_top: {a : 1, b : -238.68, c : 1.2345679},
            constant_left: {a : 1, b : 502.9, c : 1.2345679},
        },
        box: {
            constant_top: {a : 1, b : 0, c : 1},
            constant_left: {a : 1, b : 0, c : 1},
        }
    },


};

var clicked_x = 0
var clicked_y = 0
var config = {}
var currentAngle = 0; // 현재 회전 각도를 추적하기 위한 변수

function applyStyles(elementId, styleConfig) {
    const element = document.getElementById(elementId);
    if (element) {
        Object.assign(element.style, styleConfig);
    }
}

// 테스트베드 이름, 층 정보를 세팅
// mode에는 "test", "setting", "history"를 넣을 수 있음.
// [mode 설명]
// "test" : 큰 점, 큰 화살표 표시 (실시간 테스트를 위한 모드. 점 하나만 표시)
// "setting" : X, 작은 화살표 표시 (맵 수집을 위한 모드. 시작 위치/방향 설정 모드)
// "history" : 작은 점, 작은 화살표 표시 (맵 수집을 위한 모드. 점 history들 표시)
function setTestbed(testbedName="coex", floor="B1", mode="test") {
    config = testbedConfigs[testbedName];

    // mode에 따라 보여야될 요소들 정의
    const modeConfig = {
        test: { show: ['big_dot', 'big_arrow', 'box'] },
        setting: { show: ['X', 'small_arrow'] },
        history: { show: ['small_dot', 'small_arrow'] }
    };
    // 스타일 적용
    Object.entries(testbedConfigs[testbedName]).forEach(([elementId, styleConfig]) => {
        applyStyles(elementId, styleConfig);
    });

    // 맵 이미지 변경
    document.getElementById('map_img').src = `./images/maps/${testbedName}/${testbedName}_${floor}F.png`;

    // 아이콘 표시 설정
    document.querySelectorAll('.icon').forEach(icon => {
        icon.style.display = modeConfig[mode].show.includes(icon.id) ? 'block' : 'none';
    });

    const dotContainer = document.getElementById("dotContainer");
    dotContainer.style.left = '-1000px';
    dotContainer.style.top = '-1000px';


    if (mode === "setting") {
        enableTouchEvent();
    }else{
        disableTouchEvent()
    }
}

function enableTouchEvent() {
    const isTouchDevice = (navigator.maxTouchPoints || 'ontouchstart' in document.documentElement);

    // 이벤트 핸들러를 명시적으로 정의합니다.
    touchEventHandler = function(event) {
        setupTouchEvent(isTouchDevice);
    }

    if (document.readyState === "loading") {  // If document is still loading, wait for it to complete
        document.addEventListener("DOMContentLoaded", touchEventHandler, false);
    } else {  // `DOMContentLoaded` has already fired
        setupTouchEvent(isTouchDevice);
    }
}

function disableTouchEvent() {
    // isTouchDevice 값에 따라 등록된 이벤트 리스너를 제거합니다.
    const isTouchDevice = (navigator.maxTouchPoints || 'ontouchstart' in document.documentElement);
    if (isTouchDevice) {
        document.body.removeEventListener('touchstart', handleEvent, false);
    } else {
        document.body.removeEventListener('click', handleEvent, false);
    }

}

let handleEvent

function setupTouchEvent(is_touch_device) {
    handleEvent = function (event) {
        let x, y;
        if (event.type === 'touchstart') {
            // 터치 이벤트의 경우
            const touch = event.touches[0]; // 첫 번째 터치 포인트
            x = touch.pageX - this.offsetLeft;
            y = touch.pageY - this.offsetTop;
        } else {
            // 마우스 이벤트의 경우
            x = event.pageX - this.offsetLeft;
            y = event.pageY - this.offsetTop;
        }
        const dotContainer = document.getElementById("dotContainer");
        Object.assign(dotContainer.style, {
            left: `${x - 6}px`,
            top: `${y - 8}px`
        });
        function calculateCoordinate(value, config) {
            return (config.a * Number(value.replace("px", "")) + config.b) * config.c;
        }
        if (!config.x_y_axis_rotation) {
            clicked_x = calculateCoordinate(dotContainer.style.top, config.X.constant_top);
            clicked_y = calculateCoordinate(dotContainer.style.left, config.X.constant_left);
        } else {
            clicked_y = calculateCoordinate(dotContainer.style.top, config.X.constant_top);
            clicked_x = calculateCoordinate(dotContainer.style.left, config.X.constant_left);
        }
        pos_x = clicked_x;
        pos_y = clicked_y;
        console.log(clicked_x, clicked_y);
    }

    const map_img = document.getElementsByTagName("body")[0];
    map_img.addEventListener(is_touch_device ? 'touchstart' : 'click', handleEvent, false);
}

// 점 또는 X 이미지에 화살표의 각도 설정
function rotateArrow(targetAngle) {
    const arrow = document.querySelector('#small_arrow, #big_arrow').style.display === 'block' ?
        document.querySelector('#small_arrow') : document.querySelector('#big_arrow');

    // 현재 각도와 목표 각도 사이의 차이를 계산합니다.
    var angleDifference = targetAngle - currentAngle;
    // -180도와 +180도 사이의 범위로 각도 차이를 정규화합니다.
    angleDifference = (angleDifference + 180) % 360 - 180;

    // 최소 회전을 위해 각도 차이가 180보다 크면 360에서 각도 차이를 뺍니다.
    if (angleDifference > 180) {
        angleDifference -= 360;
    }
    // 최소 회전을 위해 각도 차이가 -180보다 작으면 360을 더합니다.
    else if (angleDifference < -180) {
        angleDifference += 360;
    }

    // 현재 각도에 각도 차이를 더하여 새로운 회전 각도를 계산합니다.
    currentAngle += angleDifference;

    // 화살표 이미지를 새로운 각도로 회전시킵니다.
    if (!config.x_y_axis_rotation)
        arrow.style.transform = `rotate(${currentAngle}deg)`;
    else
        arrow.style.transform = `rotate(${currentAngle - 90}deg)`;
}

// 터치한 곳의 좌표값을 가져오기 위한 함수 (안드로이드 전용)
var getClickedPosition = function () {
    return clicked_x + "\t" + clicked_y
}

function show_my_position_with_history(x_pos, y_pos) {
    const dot = document.getElementById("dotContainer");
    const {a: aTop, b: bTop, c: cTop} = config.small_dot.constant_top;
    const {a: aLeft, b: bLeft, c: cLeft} = config.small_dot.constant_left;
    if (!config.x_y_axis_rotation) {
        dot.style.top = `${(aTop * x_pos + bTop) * cTop}px`;
        dot.style.left = `${(aLeft * y_pos + bLeft) * cLeft}px`;
    }
    else {
        dot.style.top = `${(aTop * y_pos + bTop) * cTop}px`;
        dot.style.left = `${(aLeft * x_pos + bLeft) * cLeft}px`;
    }


    const smallDot = document.getElementById("small_dot").cloneNode(true); // small_dot을 복제
    smallDot.style.position = "absolute"; // 복제된 small_dot의 위치 설정을 절대 위치로 변경
    smallDot.style.zIndex = "6"
    smallDot.style.top = `${parseFloat(dot.style.top)+2}px`;
    smallDot.style.left = `${parseFloat(dot.style.left)+3}px`;

    document.body.appendChild(smallDot); // 복제된 small_dot을 body에 추가
}

function show_my_position(x_pos, y_pos) {
    const dot = document.getElementById("dotContainer");
    if (dot.style.display === "none")
        dot.style.display = "block"
    const {a: aTop, b: bTop, c: cTop} = config.big_dot.constant_top;
    const {a: aLeft, b: bLeft, c: cLeft} = config.big_dot.constant_left;
    if (!config.x_y_axis_rotation) {
        dot.style.top = `${(aTop * x_pos + bTop) * cTop}px`;
        dot.style.left = `${(aLeft * y_pos + bLeft) * cLeft}px`;
    }
    else {
        dot.style.top = `${(aTop * y_pos + bTop) * cTop}px`;
        dot.style.left = `${(aLeft * x_pos + bLeft) * cLeft}px`;
    }
}

function showArea(x_min, x_max, y_min, y_max) {
    const box = document.getElementById("box");
    const {a: aTop, b: bTop, c: cTop} = config.box.constant_top;
    const {a: aLeft, b: bLeft, c: cLeft} = config.box.constant_left;
    box.style.top = `${(aTop * x_max + bTop) * cTop}px`;
    box.style.left = `${(aLeft * y_min + bLeft) * cLeft}px`;
    box.style.height = `${(x_max - x_min)*cTop}px`
    box.style.width = `${(y_max - y_min)*cLeft}px`
    box.style.display = 'block';
}

function removeArea(){
    const box = document.getElementById("box");
    box.style.display = 'none';
}

function saveHistoryAsCSV() {
    // CSV 문자열의 첫 번째 줄에 컬럼 이름을 정의합니다.
    let csvContent = "data:text/csv;charset=utf-8," + "x,y,z\n";
    
    // positionHistory 배열의 각 항목을 순회하며 CSV 문자열을 구성합니다.
    for (let i = 0; i < positionHistory.length; i++) {
        let row = positionHistory[i].pos_x + "," + positionHistory[i].pos_y + "," + positionHistory[i].pos_z;
        csvContent += row + "\r\n"; // 각 행 끝에 줄바꿈 문자를 추가합니다.
    }
    
    // encodeURI를 사용해 CSV 문자열을 URI 컴포넌트로 변환합니다.
    const encodedUri = encodeURI(csvContent);
    
    // 다운로드 링크를 생성합니다.
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", "position_history.csv");
    
    // 이 링크를 DOM에 추가하고 클릭 이벤트를 발생시켜 파일 다운로드를 시작합니다.
    document.body.appendChild(link); // 필요한 경우에만 DOM에 추가합니다.
    link.click(); // 클릭 이벤트를 발생시킵니다.
    
    // 다운로드가 시작된 후에는 링크를 DOM에서 제거할 수 있습니다.
    document.body.removeChild(link);
    positionHistory = []; // 히스토리를 초기화합니다.
}

// 페이지 로드 시 자동으로 특정 테스트베드 설정 적용
window.onload = function () {
    // setTestbed('coex', floor="B1", mode="test"); // 기본적으로 적용할 테스트베드 이름을 여기에 입력
    setTestbed('suwonstation', floor="2", mode="setting"); // 기본적으로 적용할 테스트베드 이름을 여기에 입력
    // rotateArrow(0)
    // setTestbed('hansando', floor="0", mode="test"); // 기본적으로 적용할 테스트베드 이름을 여기에 입력
    // show_my_position_with_history(172, 2307)
    // show_my_position_with_history(112, 3510)
    // show_my_position_with_history(1304, 3510)
    // showArea(0, 5790, 0, 5790)
};

// 점 찍기 함수
function plotPoint(x, y) {
    const dot = document.createElement('div');

    dot.style.left = `${(4154/6354)*y+175}px`;
    dot.style.top = `${2230-x*(2006/3058)}px`;
    dot.style.position = 'absolute';

    dot.style.width = '10px'; // 점의 크기
    dot.style.height = '10px';
    dot.style.zIndex = 3;
    dot.style.backgroundColor = 'blue'; // 점의 색상
    dot.className = 'dot1'; // 점을 식별할 수 있는 클래스 추가
    document.body.appendChild(dot); // 생성된 점을 문서에 추가
}
function plotPointRed(x, y) {
    document.querySelectorAll('.dot2').forEach(function(dot) {
        dot.remove();
    });
    const dot = document.createElement('div');

    dot.style.left = `${(4154/6354)*y+175}px`;
    dot.style.top = `${2230-x*(2006/3058)}px`;
    dot.style.position = 'absolute';

    dot.style.width = '10px'; // 점의 크기
    dot.style.height = '10px';
    dot.style.zIndex = 3;
    dot.style.backgroundColor = 'red'; // 점의 색상
    dot.className = 'dot2'; // 점을 식별할 수 있는 클래스 추가
    document.body.appendChild(dot); // 생성된 점을 문서에 추가
}

// 모든 점 지우기 함수
function clearPoints() {
    const dots = document.querySelectorAll('.dot2'); // 문서에서 모든 점을 찾습니다.
    dots.forEach(dot => dot.remove()); // 각 점에 대해 반복하면서 문서에서 제거

    const dots2 = document.querySelectorAll('.dot1'); // 문서에서 모든 점을 찾습니다.
    dots2.forEach(dot => dot.remove()); // 각 점에 대해 반복하면서 문서에서 제거
}
