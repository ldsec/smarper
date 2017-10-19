function [varargout] = likSoftmax(hyp, y, mu, s2, inf, i)
if nargin>3
  n=size(y,1);
  C_n=size(mu,1);
  C=C_n/n;
  assert( size(s2,1) == C*n )
  assert( size(s2,2) == C*n )

  lp=sampling_m_E(y, mu, s2, 'loglikSoftmax', 'precompute_lik', generate_static_points_for_mgp(C));
  varargout = {lp};
 else
  assert(1==0);
end
