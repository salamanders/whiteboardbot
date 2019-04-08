// https://stackoverflow.com/questions/25582882/javascript-math-random-normal-distribution-gaussian-bell-curve/40044129
const gaussianRand = () => {
  let rand = 0;
  for (let i = 0; i < 6; i++) {
    rand += Math.random();
  }
  return (rand - 3) / 6;
};

