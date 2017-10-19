function [mae, icr] = loss(yHat, yTe)
% compute loss
% Written by Emtiyaz Khan (EPFL)

% Mean Absolute Error (MAE) loss
err = yHat - yTe;
idx = find(~isnan(yTe));
mae = nanmean(abs(err(idx)));

% Incorrect classification rate (ICR)
icr = 1 - sum(err(:)==0)/length(idx);

