/*jshint esversion: 6 */
/*jshint bitwise: false*/

const sourceCanvas = document.getElementById('sourceCanvas');
const scriptCanvas = document.getElementById('scriptCanvas');
const script = []; // finished when long enough

const NUM_PARTICLES = 100,
  OPTIMIZER_STEPS = 50,
  SCRIPT_LENGTH = 800;

const setDefaultImage = () => {
  const img = new Image();
  img.onload = () => {
    console.info('Starting on default image.');
    prepImage(img);
  };
  img.src = 'liberty.png';
};

const enableImageUpload = (imageEltId = 'imageLoader') => {
  // Custom file upload (optional)
  const handleImageUpload = e => {
    const reader = new FileReader();
    reader.onload = event => {
      const img = new Image();
      img.onload = () => {
        console.info('Starting on custom uploaded image.');
        prepImage(img);
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(e.target.files[0]);
  };
  const imageLoader = document.getElementById(imageEltId);
  imageLoader.addEventListener('change', handleImageUpload, false);
};

/** Once you have an image, insert it into the canvas and start playing. */
const prepImage = img => {
  sourceCanvas.width = img.width;
  sourceCanvas.height = img.height;
  scriptCanvas.width = sourceCanvas.width;
  scriptCanvas.height = sourceCanvas.height;
  sourceCanvas.getContext('2d').drawImage(img, 0, 0);

  // Convert to grayscale (could mess with contrast here)
  const sourceImageData = sourceCanvas.getContext('2d').getImageData(0, 0, sourceCanvas.width, sourceCanvas.height);
  const data = sourceImageData.data;
  for (let i = 0; i < data.length; i += 4) {
    const avg = (data[i] + data[i + 1] + data[i + 2]) / 3;
    data[i] = avg; // red
    data[i + 1] = avg; // green
    data[i + 2] = avg; // blue
  }
  sourceCanvas.getContext('2d').putImageData(sourceImageData, 0, 0);
  console.info("Converted to grayscale.");

  while (script.length > 0) {
    script.pop();
  }
  script.push([Math.floor(sourceCanvas.width / 2), Math.floor(sourceCanvas.height / 2)]);

  window.requestAnimationFrame(imageToLineDrawing);
};


/**
 * All points along a line.
 * https://stackoverflow.com/questions/4672279/bresenham-algorithm-in-javascript
 * Guard against doubles.
 */
const lineToPoints = (p1, p2) => {
  let x1 = Math.floor(p1[0]),
    y1 = Math.floor(p1[1]);
  const x2 = Math.floor(p2[0]),
    y2 = Math.floor(p2[1]);

  const coordinatesArray = [],
    dx = Math.abs(x2 - x1),
    dy = Math.abs(y2 - y1),
    sx = (x1 < x2) ? 1 : -1,
    sy = (y1 < y2) ? 1 : -1;

  let err = dx - dy;
  // Set first coordinates
  coordinatesArray.push([x1, y1]);
  // Main loop
  while (!((x1 === x2) && (y1 === y2))) {
    const e2 = err << 1;
    if (e2 > -dy) {
      err -= dy;
      x1 += sx;
    }
    if (e2 < dx) {
      err += dx;
      y1 += sy;
    }
    coordinatesArray.push([x1, y1]);
  }
  return coordinatesArray;
};

window.converge = Array(OPTIMIZER_STEPS);
window.converge.fill(255);

/** The fun part: Draw just one more step */
const imageToLineDrawing = () => {
  if (script.length % 50 === 0) {
    console.info(`Building script step ${script.length}`);
  }

  const width = sourceCanvas.width,
    height = sourceCanvas.height,
    sourceCtx = sourceCanvas.getContext('2d'),
    scriptCtx = scriptCanvas.getContext('2d');

  // Faster to get .data now once
  const sourceImageData = sourceCtx.getImageData(0, 0, width, height).data;

  /**
   * Get the red channel
   * https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API/Tutorial/Pixel_manipulation_with_canvas
   */
  const pixelToLum = p1 => sourceImageData[p1[1] * (width * 4) + p1[0] * 4];

  // So we can draw it both on the input and the output.
  const nextStepScript = new Path2D();

  const origin = script.slice(-1)[0];
  nextStepScript.moveTo(origin[0], origin[1]);

  // https://github.com/adrianton3/pso.js/
  const optimizer = new pso.Optimizer();
  optimizer.setObjectiveFunction(potentialPoint => {
    // Ignore out-of-bounds points.
    if (potentialPoint[0] < 0 || potentialPoint[0] >= width || potentialPoint[1] < 0 || potentialPoint[1] >= height) {
      return -1;
    }
    const pixelsOnLine = lineToPoints(origin, potentialPoint);
    const avgLum = pixelsOnLine.reduce((totalLum, p1) => totalLum + pixelToLum(p1), 0) / pixelsOnLine.length;
    return 255 - avgLum;
  });
  optimizer.init(NUM_PARTICLES, [{start: 0, end: width}, {start: 0, end: height}]);

  for (let i = 0; i < OPTIMIZER_STEPS; i++) {
    window.converge[i] += optimizer.getBestFitness();
    optimizer.step();
  }

  const bestNextPoint = optimizer.getBestPosition();
  console.debug(optimizer.getBestFitness(), bestNextPoint);

  nextStepScript.lineTo(bestNextPoint[0], bestNextPoint[1]);
  script.push(bestNextPoint);

  sourceCtx.strokeStyle = '#FFFFFF';
  sourceCtx.lineWidth = 2.0;
  sourceCtx.stroke(nextStepScript);
  scriptCtx.stroke(nextStepScript);


  if (script.length < SCRIPT_LENGTH) {
    window.requestAnimationFrame(imageToLineDrawing);
  } else {
    console.debug('Convergence speed:');
    for (let i = 0; i < OPTIMIZER_STEPS; i++) {
      window.converge[i] = Math.floor(window.converge[i] / SCRIPT_LENGTH);
    }
    console.debug(window.converge);
  }
};


setDefaultImage();
enableImageUpload();
